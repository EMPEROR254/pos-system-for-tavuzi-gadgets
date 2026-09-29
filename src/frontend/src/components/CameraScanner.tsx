// Scan a barcode with the phone camera (for the Android till, which has no USB
// scanner). Uses the browser's BarcodeDetector (Chrome on Android); the button
// is hidden where the browser can't do it, e.g. most desktop browsers.
import { useEffect, useRef, useState } from 'react'
import { Dialog } from './Dialog'

type Detector = { detect: (source: HTMLVideoElement) => Promise<{ rawValue: string }[]> }
type DetectorClass = new (options: { formats: string[] }) => Detector

function detectorClass(): DetectorClass | null {
  const w = window as unknown as { BarcodeDetector?: DetectorClass }
  return typeof w.BarcodeDetector === 'function' && Boolean(navigator.mediaDevices?.getUserMedia)
    ? w.BarcodeDetector
    : null
}

export function canScanWithCamera(): boolean {
  return detectorClass() !== null
}

export function CameraScanButton({ onCode }: { onCode: (code: string) => void }) {
  const [open, setOpen] = useState(false)
  if (!canScanWithCamera()) return null
  return (
    <>
      <button
        type="button"
        aria-label="Scan a barcode with the camera"
        onClick={() => setOpen(true)}
        className="flex size-12 shrink-0 items-center justify-center rounded-xl border border-border bg-card text-xl hover:bg-background"
      >
        📷
      </button>
      {open && (
        <ScanDialog
          onClose={() => setOpen(false)}
          onCode={(code) => {
            setOpen(false)
            onCode(code)
          }}
        />
      )}
    </>
  )
}

function ScanDialog({ onClose, onCode }: { onClose: () => void; onCode: (code: string) => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const Detector = detectorClass()
    if (!Detector) return
    const detector = new Detector({ formats: ['ean_13', 'ean_8', 'code_128', 'code_39', 'upc_a', 'upc_e', 'qr_code'] })
    let stream: MediaStream | null = null
    let timer: number | undefined
    let stopped = false

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' } })
      .then(async (s) => {
        stream = s
        if (stopped || !video.current) return
        video.current.srcObject = s
        await video.current.play()
        // Look for a barcode a few times a second until one is found.
        timer = window.setInterval(async () => {
          if (!video.current) return
          const codes = await detector.detect(video.current).catch(() => [])
          if (codes.length > 0 && !stopped) {
            stopped = true
            onCode(codes[0].rawValue)
          }
        }, 300)
      })
      .catch(() => setError('The camera could not start. Allow camera access for this site and try again.'))

    return () => {
      stopped = true
      window.clearInterval(timer)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [onCode])

  return (
    <Dialog open title="Scan a barcode" onClose={onClose}>
      {error ? (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : (
        <div className="space-y-2">
          <video ref={video} muted playsInline className="aspect-video w-full rounded-xl bg-black object-cover" />
          <p className="text-center text-sm text-muted">Point the camera at the barcode.</p>
        </div>
      )}
    </Dialog>
  )
}
