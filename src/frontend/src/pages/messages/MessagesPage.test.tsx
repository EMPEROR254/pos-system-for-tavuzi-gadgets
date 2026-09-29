import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as messages from '../../services/messages'
import * as products from '../../services/products'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/messages', async (original) => ({
  ...(await original<typeof messages>()),
  listMessages: vi.fn(),
  previewCampaign: vi.fn(),
  sendCampaign: vi.fn(),
  listCampaigns: vi.fn(),
  listReplies: vi.fn(),
  smsAccount: vi.fn(),
  resendMessage: vi.fn(),
}))
vi.mock('../../services/products', async (original) => ({
  ...(await original<typeof products>()),
  productCategories: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(products.productCategories).mockResolvedValue(['Phones', 'Accessories'])
  vi.mocked(messages.listMessages).mockResolvedValue({
    content: [
      {
        id: 1,
        phone: '254712345678',
        body: 'TAVUZI GADGETS receipt TG000001: KES 1,300 paid by Cash. Thank you!',
        kind: 'RECEIPT',
        status: 'SENT',
        attempts: 1,
        error: null,
        createdAt: '2026-09-28T07:00:00Z',
        sentAt: '2026-09-28T07:00:05Z',
        sendAfter: null,
        deliveryStatus: 'Success',
        failureReason: null,
      },
      {
        id: 2,
        phone: '254722000111',
        body: 'Earphones 20% off! Reply STOP to opt out.',
        kind: 'PROMO',
        status: 'SENT',
        attempts: 1,
        error: null,
        createdAt: '2026-09-28T07:00:00Z',
        sentAt: '2026-09-28T07:00:05Z',
        sendAfter: null,
        deliveryStatus: 'Failed',
        failureReason: 'AbsentSubscriber',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
  })
  vi.mocked(messages.listCampaigns).mockResolvedValue([
    {
      id: 7,
      message: 'Earphones 20% off! Reply STOP to opt out.',
      audience: 'All customers who agreed',
      createdAt: '2026-09-28T07:00:00Z',
      sendAt: null,
      recipients: 40,
      sent: 38,
      delivered: 35,
      failed: 3,
      waiting: 2,
    },
  ])
  vi.mocked(messages.listReplies).mockResolvedValue({
    content: [{ id: 1, phone: '254733000222', text: 'STOP', optedOut: true, receivedAt: '2026-09-28T08:00:00Z' }],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  })
  vi.mocked(messages.smsAccount).mockResolvedValue({ live: true, balance: 'KES 1,250.00', quietHours: '20:00-08:00' })
  vi.mocked(messages.resendMessage).mockResolvedValue(undefined)
  vi.mocked(messages.sendCampaign).mockClear()
})

describe('MessagesPage', () => {
  it('shows sent SMS with whether they were delivered, and sends undelivered ones again', async () => {
    renderApp('/messages', 'MANAGER')
    expect(await screen.findByText(/receipt TG000001/)).toBeInTheDocument()
    const receipt = screen.getByText('0712 345 678 · Receipt').closest('li')!
    expect(within(receipt).getByText('Delivered')).toBeInTheDocument()
    expect(screen.getByText('Not delivered')).toBeInTheDocument()
    expect(screen.getByText(/AbsentSubscriber/)).toBeInTheDocument()

    const again = screen.getAllByRole('button', { name: 'Send again' })
    expect(again).toHaveLength(1)
    await userEvent.click(again[0])
    expect(messages.resendMessage).toHaveBeenCalledWith(2)
    expect(await screen.findByText('SMS will be sent again.')).toBeInTheDocument()
  })

  it('shows the SMS balance, how promotions did and customers replies', async () => {
    renderApp('/messages', 'OWNER')
    expect(await screen.findByText('SMS account balance: KES 1,250.00')).toBeInTheDocument()
    expect(await screen.findByText('2 still waiting to go out.')).toBeInTheDocument()
    expect(screen.getByText('35')).toBeInTheDocument()
    expect(await screen.findByText('Opted out')).toBeInTheDocument()
    expect(screen.getByText(/No promotions 20:00-08:00/)).toBeInTheDocument()
  })

  it('schedules a promotion for a time in Nairobi', async () => {
    vi.mocked(messages.previewCampaign).mockResolvedValue({ recipients: 5, fullMessage: 'Hi', smsParts: 1 })
    vi.mocked(messages.sendCampaign).mockResolvedValue({
      campaignId: 2,
      recipients: 5,
      smsParts: 1,
      sendsAt: '2099-10-01T11:00:00Z',
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/messages', 'OWNER')

    await userEvent.type(await screen.findByLabelText('Message'), 'Hi')
    await userEvent.type(screen.getByLabelText('Send at (optional)'), '2099-10-01T14:00')
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }))
    await screen.findByText('5')
    await userEvent.click(screen.getByRole('button', { name: 'Schedule' }))
    expect(await screen.findByText(/will go out/)).toBeInTheDocument()
    expect(vi.mocked(messages.sendCampaign).mock.calls[0][0].sendAt).toBe('2099-10-01T11:00:00.000Z')
  })

  it('previews who gets a promotion, then sends it after confirming', async () => {
    vi.mocked(messages.previewCampaign).mockResolvedValue({
      recipients: 42,
      fullMessage: 'New phones in stock! Reply STOP to opt out.',
      smsParts: 1,
    })
    vi.mocked(messages.sendCampaign).mockResolvedValue({
      campaignId: 1,
      recipients: 42,
      smsParts: 1,
      sendsAt: new Date().toISOString(),
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/messages', 'OWNER')

    await userEvent.type(await screen.findByLabelText('Message'), 'New phones in stock!')
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Only customers who bought (optional)'), 'Phones')
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }))
    expect(await screen.findByText('42')).toBeInTheDocument()
    expect(screen.getByText('“New phones in stock! Reply STOP to opt out.”')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByText('Promotion queued for 42 customers.')).toBeInTheDocument()
    expect(messages.sendCampaign).toHaveBeenCalledWith({
      message: 'New phones in stock!',
      boughtWithinDays: undefined,
      category: 'Phones',
      sendAt: undefined,
    })
  })

  it('is not for cashiers', async () => {
    renderApp('/messages', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})
