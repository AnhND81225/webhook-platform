import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useCallback } from 'react'
import { expect, it } from 'vitest'
import { useResource } from './useResource'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

it('does not let an older request overwrite a newer local resource update', async () => {
  const initialRequest = deferred<string[]>()
  function ResourceHarness() {
    const load = useCallback(() => initialRequest.promise, [])
    const resource = useResource('applications', load)
    return <>
      <button type="button" onClick={() => resource.update((current) => ['new-application', ...(current ?? [])])}>Add application</button>
      <output>{resource.data?.join(',') ?? 'loading'}</output>
    </>
  }

  render(<ResourceHarness />)
  expect(screen.getByText('loading')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Add application' }))
  expect(screen.getByText('new-application')).toBeInTheDocument()
  initialRequest.resolve(['stale-application'])
  await Promise.resolve()
  expect(screen.getByText('new-application')).toBeInTheDocument()
  expect(screen.queryByText('stale-application')).not.toBeInTheDocument()
})
