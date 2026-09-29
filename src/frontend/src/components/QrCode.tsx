// A QR code drawn as SVG (sharp when printed). The library loads only with the
// screens that show one, never on the till's first screen.
import { useEffect, useState } from 'react'

export function QrCode({ value, label, size = 120 }: { value: string; label: string; size?: number }) {
  const [svg, setSvg] = useState<string | null>(null)
  useEffect(() => {
    let active = true
    void import('qrcode').then((qr) =>
      qr.toString(value, { type: 'svg', margin: 1, errorCorrectionLevel: 'L' }).then((text) => {
        if (active) setSvg(text)
      }),
    )
    return () => {
      active = false
    }
  }, [value])
  if (!svg) return <div style={{ width: size, height: size }} className="mx-auto" aria-hidden />
  return (
    <img
      src={`data:image/svg+xml;utf8,${encodeURIComponent(svg)}`}
      alt={label}
      width={size}
      height={size}
      className="mx-auto bg-white"
    />
  )
}
