import { screen } from '@testing-library/react'
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
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  })
})

describe('MessagesPage', () => {
  it('shows sent SMS with their status', async () => {
    renderApp('/messages', 'MANAGER')
    expect(await screen.findByText(/receipt TG000001/)).toBeInTheDocument()
    expect(screen.getByText('0712 345 678 · Receipt')).toBeInTheDocument()
    expect(screen.getByText('Sent')).toBeInTheDocument()
  })

  it('previews who gets a promotion, then sends it after confirming', async () => {
    vi.mocked(messages.previewCampaign).mockResolvedValue({
      recipients: 42,
      fullMessage: 'New phones in stock! Reply STOP to opt out.',
      smsParts: 1,
    })
    vi.mocked(messages.sendCampaign).mockResolvedValue({ campaignId: 1, recipients: 42, smsParts: 1 })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderApp('/messages', 'OWNER')

    await userEvent.type(await screen.findByLabelText('Message'), 'New phones in stock!')
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Only customers who bought (optional)'), 'Phones')
    await userEvent.click(screen.getByRole('button', { name: 'Preview' }))
    expect(await screen.findByText('42')).toBeInTheDocument()
    expect(screen.getByText(/Reply STOP to opt out/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByText('Promotion queued for 42 customers.')).toBeInTheDocument()
    expect(messages.sendCampaign).toHaveBeenCalledWith({
      message: 'New phones in stock!',
      boughtWithinDays: undefined,
      category: 'Phones',
    })
  })

  it('is not for cashiers', async () => {
    renderApp('/messages', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})
