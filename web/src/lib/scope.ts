import type { AdminRole } from './api'

/**
 * What a coordinator's batch assignment actually is, as opposed to what a
 * from–to pair can say about it.
 *
 * The portal used to describe every assignment as `batches[0]`–`batches[last]`.
 * That is wrong twice over: it reads a range out of a set that need not be
 * contiguous — {1998, 2015} rendered as "1998–2015", eighteen batches instead
 * of two — and it silently turned a super admin's empty scope and a group
 * admin's empty scope into the same thing, when one means every batch and the
 * other means none.
 */
export type BatchScope =
  | { kind: 'ALL' }
  | { kind: 'NONE' }
  | { kind: 'RANGE'; from: number; to: number; count: number }
  | { kind: 'LIST'; years: number[]; count: number }

/** Years must be ascending; `adminApi` guarantees that on the way in. */
export function batchScope(role: AdminRole, batches: number[] | undefined): BatchScope {
  if (role === 'SUPER_ADMIN') return { kind: 'ALL' }

  const years = batches ?? []
  if (years.length === 0) return { kind: 'NONE' }

  const from = years[0]
  const to = years[years.length - 1]
  const contiguous = to - from + 1 === years.length
  return contiguous
    ? { kind: 'RANGE', from, to, count: years.length }
    : { kind: 'LIST', years, count: years.length }
}

/** True when a from–to pair can express this assignment without changing it. */
export function isRangeExpressible(scope: BatchScope): boolean {
  return scope.kind !== 'LIST'
}
