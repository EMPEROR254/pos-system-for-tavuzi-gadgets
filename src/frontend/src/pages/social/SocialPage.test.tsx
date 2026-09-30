import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as social from '../../services/social'
import { renderApp } from '../../test/renderApp'
import * as navigate from '../../utils/navigate'

vi.mock('../../services/social', async (original) => ({
  ...(await original<typeof social>()),
  listAccounts: vi.fn(),
  listPosts: vi.fn(),
  startLink: vi.fn(),
  createPost: vi.fn(),
  retryPost: vi.fn(),
  setWhatsAppNumber: vi.fn(),
  unlinkAccount: vi.fn(),
}))
vi.mock('../../utils/navigate', () => ({ goToExternal: vi.fn() }))

const account = (network: social.SocialNetwork, linked: boolean, name: string | null = null): social.SocialAccount => ({
  network,
  linked,
  name,
  linkedAt: linked ? '2026-09-28T07:00:00Z' : null,
  canLink: true,
  setup: null,
})

const failedPost: social.SocialPost = {
  id: 5,
  title: 'Earbuds 20% off',
  description: 'This week only',
  caption: 'Earbuds 20% off\n\nThis week only',
  imageUrl: '/api/social/images/abc.jpg',
  createdByName: 'Test owner',
  createdAt: '2026-09-28T07:00:00Z',
  targets: [
    { network: 'FACEBOOK', status: 'POSTED', error: null, attempts: 1 },
    { network: 'INSTAGRAM', status: 'FAILED', error: 'Instagram: the picture could not be fetched', attempts: 3 },
    { network: 'TIKTOK', status: 'SENT_TO_INBOX', error: null, attempts: 1 },
  ],
}

const picture = () => new File(['png-bytes'], 'phone.png', { type: 'image/png' })

beforeEach(() => {
  vi.mocked(social.listAccounts).mockResolvedValue([
    account('FACEBOOK', true, 'Tavuzi Gadgets'),
    account('INSTAGRAM', true, '@tavuzigadgets'),
    account('TIKTOK', false),
    account('WHATSAPP', true, '+254712345678'),
  ])
  vi.mocked(social.listPosts).mockResolvedValue({
    content: [failedPost],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  })
  vi.mocked(social.createPost).mockReset().mockResolvedValue(failedPost)
  vi.mocked(social.retryPost).mockReset().mockResolvedValue(undefined)
  vi.mocked(social.startLink).mockReset().mockResolvedValue({ authorizeUrl: 'https://tiktok.test/auth?state=x' })
  vi.mocked(navigate.goToExternal).mockReset()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  Object.assign(navigator, { share: undefined, canShare: undefined })
})

describe('SocialPage', () => {
  it('posts a picture with text to the chosen linked accounts', async () => {
    renderApp('/social', 'MANAGER')
    await userEvent.type(await screen.findByLabelText('Post'), 'New Samsung A15 in stock!')
    await userEvent.type(screen.getByLabelText('Description'), 'KES 18,999 at Resma Plaza')
    await userEvent.upload(screen.getByLabelText('Picture'), picture())
    expect(screen.getByAltText('The picture to post')).toBeInTheDocument()

    // TikTok isn't linked, so it can't be chosen.
    const postTo = screen.getByRole('group', { name: 'Post to' })
    expect(within(postTo).getByRole('checkbox', { name: /TikTok/ })).toBeDisabled()
    await userEvent.click(within(postTo).getByRole('checkbox', { name: /Facebook/ }))
    await userEvent.click(within(postTo).getByRole('checkbox', { name: /Instagram/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: /Order on WhatsApp/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Post now' }))

    expect(await screen.findByText('Posting to Facebook, Instagram...')).toBeInTheDocument()
    const sent = vi.mocked(social.createPost).mock.calls[0][0]
    expect(sent).toMatchObject({
      title: 'New Samsung A15 in stock!',
      description: 'KES 18,999 at Resma Plaza',
      networks: ['FACEBOOK', 'INSTAGRAM'],
      whatsAppLink: true,
    })
    expect(sent.image.name).toBe('phone.png')
  })

  it('needs a post, a picture and at least one account', async () => {
    renderApp('/social', 'OWNER')
    await userEvent.click(await screen.findByRole('button', { name: 'Post now' }))
    expect(await screen.findByText('Write the post.')).toBeInTheDocument()
    expect(screen.getByText('Choose where to post.')).toBeInTheDocument()
    expect(social.createPost).not.toHaveBeenCalled()
  })

  it('shares to WhatsApp from the phone with the picture and text', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { share, canShare: () => true })
    renderApp('/social', 'OWNER')
    await userEvent.type(await screen.findByLabelText('Post'), 'Weekend offer')
    await userEvent.upload(screen.getByLabelText('Picture'), picture())
    await userEvent.click(screen.getByRole('checkbox', { name: /WhatsApp\s*Share from this phone/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Post now' }))

    expect(share).toHaveBeenCalledWith({ files: [expect.any(File)], text: 'Weekend offer' })
    expect(await screen.findByText('Post saved.')).toBeInTheDocument()
    expect(vi.mocked(social.createPost).mock.calls[0][0].networks).toEqual(['WHATSAPP'])
  })

  it('shows how each account went and tries a failed one again', async () => {
    renderApp('/social', 'MANAGER')
    expect(await screen.findByText('Facebook: Posted')).toBeInTheDocument()
    expect(screen.getByText('TikTok: In TikTok inbox')).toBeInTheDocument()
    expect(screen.getByText('Instagram: the picture could not be fetched')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(social.retryPost).toHaveBeenCalledWith(5, 'INSTAGRAM')
    expect(await screen.findByText('Sending to Instagram again.')).toBeInTheDocument()
  })

  it('lets the owner link TikTok on its sign-in page, but not a manager', async () => {
    renderApp('/social', 'OWNER')
    await userEvent.click(await screen.findByRole('button', { name: 'Link TikTok' }))
    expect(social.startLink).toHaveBeenCalledWith('TIKTOK')
    expect(navigate.goToExternal).toHaveBeenCalledWith('https://tiktok.test/auth?state=x')
  })

  it('hides linking from managers', async () => {
    renderApp('/social', 'MANAGER')
    await screen.findByText('Accounts')
    expect(screen.queryByRole('button', { name: 'Link TikTok' })).not.toBeInTheDocument()
  })

  it('says which accounts were linked on the way back from Facebook', async () => {
    renderApp('/social?linked=facebook,instagram', 'OWNER')
    expect(await screen.findByText('Linked Facebook and Instagram.')).toBeInTheDocument()
  })

  it('is not for cashiers', async () => {
    renderApp('/social', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})
