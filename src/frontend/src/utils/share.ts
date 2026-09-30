// Sharing a post to WhatsApp. WhatsApp has no API for posting to Status or a
// Channel, so on phones we open the share sheet with the picture and text (the
// owner picks WhatsApp, then "My status" or a chat). Laptops without file
// sharing open WhatsApp with the text; the picture is then attached by hand.

export type ShareResult = 'shared' | 'text-only' | 'cancelled'

export async function shareToWhatsApp(text: string, picture?: File): Promise<ShareResult> {
  const withPicture = picture ? { files: [picture], text } : { text }
  if (navigator.share && (!picture || navigator.canShare?.(withPicture))) {
    try {
      await navigator.share(withPicture)
      return 'shared'
    } catch {
      // The owner closed the share sheet.
      return 'cancelled'
    }
  }
  window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener')
  return 'text-only'
}

/** A saved post's picture as a file, so it can be shared again later. */
export async function pictureFromUrl(url: string, name = 'post.jpg'): Promise<File | undefined> {
  try {
    const blob = await (await fetch(url)).blob()
    return new File([blob], name, { type: blob.type || 'image/jpeg' })
  } catch {
    return undefined
  }
}
