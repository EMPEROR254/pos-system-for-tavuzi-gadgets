// Write a post: headline, description and a picture, then choose the accounts.
// Facebook, Instagram and TikTok are posted by the server in the background;
// WhatsApp opens this phone's share sheet (WhatsApp has no posting API).
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ApiError } from '../../services/api'
import { createPost, NETWORK_LABELS, type SocialAccount, type SocialNetwork } from '../../services/social'
import { shareToWhatsApp } from '../../utils/share'
import { toast } from '../../utils/toast'

const schema = z.object({
  title: z.string().trim().min(1, 'Write the post.').max(150, 'At most 150 characters.'),
  description: z.string().max(2000, 'At most 2000 characters.'),
  networks: z.array(z.enum(['FACEBOOK', 'INSTAGRAM', 'TIKTOK', 'WHATSAPP'])).min(1, 'Choose where to post.'),
  whatsAppLink: z.boolean(),
})
type PostForm = z.infer<typeof schema>

/** Same text the server posts: headline, description, then the WhatsApp link. */
export function captionOf(title: string, description: string, whatsAppNumber?: string | null) {
  let text = title.trim()
  if (description.trim()) text += `\n\n${description.trim()}`
  if (whatsAppNumber) text += `\n\nOrder on WhatsApp: https://wa.me/${whatsAppNumber.replace(/\D/g, '')}`
  return text
}

export function PostComposer({ accounts }: { accounts: SocialAccount[] }) {
  const queryClient = useQueryClient()
  const [picture, setPicture] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [ratio, setRatio] = useState<number | null>(null)
  const [pictureError, setPictureError] = useState<string>()
  const { register, handleSubmit, watch, reset, formState } = useForm<PostForm>({
    resolver: zodResolver(schema),
    defaultValues: { title: '', description: '', networks: [], whatsAppLink: false },
  })
  const whatsApp = accounts.find((a) => a.network === 'WHATSAPP')
  const chosen = watch('networks')
  const title = watch('title')
  const description = watch('description')

  // Free the preview's memory when the picture changes or the screen closes.
  useEffect(() => () => void (preview && URL.revokeObjectURL(preview)), [preview])

  const pick = (file: File | undefined) => {
    setPictureError(undefined)
    setRatio(null)
    if (!file) return
    if (!['image/jpeg', 'image/png'].includes(file.type)) return setPictureError('Use a JPEG or PNG picture.')
    if (file.size > 8 * 1024 * 1024) return setPictureError('That picture is over 8 MB.')
    setPicture(file)
    const url = URL.createObjectURL(file)
    setPreview(url)
    const img = new Image()
    img.onload = () => setRatio(img.naturalWidth / img.naturalHeight)
    img.src = url
  }

  // Instagram only takes pictures between 4:5 portrait and 1.91:1 landscape.
  const instagramShape =
    chosen.includes('INSTAGRAM') && ratio !== null && (ratio < 0.795 || ratio > 1.915)
      ? 'Instagram needs a picture between 4:5 (portrait) and 1.91:1 (landscape). Crop it, or untick Instagram.'
      : null

  const submit = handleSubmit(async (values) => {
    if (!picture) return setPictureError('Add a picture.')
    const names = values.networks.map((n) => NETWORK_LABELS[n]).join(', ')
    if (!window.confirm(`Post this to ${names}?`)) return
    // Open the share sheet first, while the tap still counts (browsers require it).
    if (values.networks.includes('WHATSAPP')) {
      void shareToWhatsApp(
        captionOf(values.title, values.description, values.whatsAppLink ? whatsApp?.name : null),
        picture,
      )
    }
    try {
      await createPost({ ...values, image: picture })
      const server = values.networks.filter((n) => n !== 'WHATSAPP')
      toast.success(server.length ? `Posting to ${server.map((n) => NETWORK_LABELS[n]).join(', ')}...` : 'Post saved.')
      reset()
      setPicture(null)
      setPreview(null)
      await queryClient.invalidateQueries({ queryKey: ['social', 'posts'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not post that.')
    }
  })

  return (
    <form
      onSubmit={(e) => void submit(e)}
      noValidate
      className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm"
    >
      <h2 className="font-semibold">New post</h2>
      <div className="space-y-1.5 text-sm">
        <label htmlFor="post-title" className="block font-medium">
          Post
        </label>
        <input
          id="post-title"
          placeholder="e.g. New Samsung A15 in stock!"
          aria-invalid={formState.errors.title ? true : undefined}
          className={inputClass}
          {...register('title')}
        />
        <p className={`text-xs ${formState.errors.title ? 'text-danger' : 'text-muted'}`}>
          {formState.errors.title?.message ?? `${title.length}/150`}
        </p>
      </div>
      <div className="space-y-1.5 text-sm">
        <label htmlFor="post-description" className="block font-medium">
          Description
        </label>
        <textarea
          id="post-description"
          rows={4}
          placeholder="Price, where to find you, hashtags..."
          className="block w-full rounded-xl border border-border bg-background p-3 text-base sm:text-sm"
          {...register('description')}
        />
        <p className={`text-xs ${formState.errors.description ? 'text-danger' : 'text-muted'}`}>
          {formState.errors.description?.message ?? `${description.length}/2000`}
        </p>
      </div>
      <div className="space-y-1.5 text-sm">
        <label htmlFor="post-picture" className="block font-medium">
          Picture
        </label>
        <input
          id="post-picture"
          type="file"
          accept="image/jpeg,image/png"
          onChange={(e) => pick(e.target.files?.[0])}
          className="block w-full text-sm file:mr-3 file:min-h-11 file:rounded-xl file:border-0 file:bg-brand-soft file:px-4 file:font-medium"
        />
        {pictureError && <p className="text-sm text-danger">{pictureError}</p>}
        {preview && (
          <img src={preview} alt="The picture to post" className="max-h-64 rounded-xl border border-border" />
        )}
      </div>

      <fieldset className="space-y-2 text-sm">
        <legend className="mb-1 font-medium">Post to</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {accounts.map((a) => {
            // WhatsApp is shared from this phone, so it never needs linking.
            const available = a.network === 'WHATSAPP' || a.linked
            return (
              <label
                key={a.network}
                className={`flex min-h-11 items-center gap-3 rounded-xl border border-border px-3 ${
                  available ? '' : 'opacity-60'
                }`}
              >
                <input
                  type="checkbox"
                  value={a.network}
                  disabled={!available}
                  className="size-5 accent-[var(--brand)]"
                  {...register('networks')}
                />
                <span>
                  {NETWORK_LABELS[a.network as SocialNetwork]}
                  <span className="block text-xs text-muted">
                    {a.network === 'WHATSAPP' ? 'Share from this phone' : a.linked ? a.name : 'Not linked'}
                  </span>
                </span>
              </label>
            )
          })}
        </div>
        {formState.errors.networks && <p className="text-danger">{formState.errors.networks.message}</p>}
        <label className="flex min-h-11 items-center gap-3">
          <input
            type="checkbox"
            disabled={!whatsApp?.linked}
            className="size-5 accent-[var(--brand)]"
            {...register('whatsAppLink')}
          />
          <span>
            Add an &quot;Order on WhatsApp&quot; link
            {!whatsApp?.linked && <span className="text-muted"> (save the WhatsApp number first)</span>}
          </span>
        </label>
      </fieldset>
      {instagramShape && (
        <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
          {instagramShape}
        </p>
      )}
      <Button type="submit" busy={formState.isSubmitting} disabled={!!instagramShape}>
        Post now
      </Button>
    </form>
  )
}
