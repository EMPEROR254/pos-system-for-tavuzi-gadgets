import { describe, expect, it } from 'vitest'
import { addDays, quickRanges } from './dateRanges'

describe('quick date ranges', () => {
  it('works out weeks (from Monday) and months in Nairobi days', () => {
    // 15 March 2027 is a Monday; 17 March a Wednesday.
    const ranges = Object.fromEntries(quickRanges('2027-03-17').map((r) => [r.label, [r.from, r.to]]))
    expect(ranges['Today']).toEqual(['2027-03-17', '2027-03-17'])
    expect(ranges['Yesterday']).toEqual(['2027-03-16', '2027-03-16'])
    expect(ranges['This week']).toEqual(['2027-03-15', '2027-03-17'])
    expect(ranges['This month']).toEqual(['2027-03-01', '2027-03-17'])
    expect(ranges['Last month']).toEqual(['2027-02-01', '2027-02-28'])
    expect(ranges['Last 30 days']).toEqual(['2027-02-16', '2027-03-17'])
  })

  it('crosses month and year ends', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(quickRanges('2027-01-03').find((r) => r.label === 'Last month')).toMatchObject({
      from: '2026-12-01',
      to: '2026-12-31',
    })
  })
})
