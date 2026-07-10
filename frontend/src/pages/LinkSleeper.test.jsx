import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

const mockNavigate = vi.fn()
const mockLinkSleeper = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { displayName: 'Jane' },
    linkSleeper: mockLinkSleeper,
  }),
}))

import LinkSleeper from './LinkSleeper'

function renderLinkSleeper() {
  return render(
    <MemoryRouter>
      <LinkSleeper />
    </MemoryRouter>
  )
}

describe('LinkSleeper page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('greets the user by display name', () => {
    renderLinkSleeper()
    expect(screen.getByText(/Hi Jane!/)).toBeInTheDocument()
  })

  it('disables the submit button until a username is entered', async () => {
    renderLinkSleeper()
    const button = screen.getByRole('button', { name: 'Link Sleeper Account' })
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoops')
    expect(button).toBeEnabled()
  })

  it('trims the username, links, and navigates to /select-league', async () => {
    mockLinkSleeper.mockResolvedValue({})
    renderLinkSleeper()

    await userEvent.type(screen.getByLabelText('Sleeper Username'), '  hoopsdynasty  ')
    await userEvent.click(screen.getByRole('button', { name: 'Link Sleeper Account' }))

    expect(mockLinkSleeper).toHaveBeenCalledWith('hoopsdynasty')
    expect(mockNavigate).toHaveBeenCalledWith('/select-league')
  })

  it('shows the server error message on failure', async () => {
    mockLinkSleeper.mockRejectedValue({ response: { data: { message: 'Nope' } } })
    renderLinkSleeper()

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'ghost')
    await userEvent.click(screen.getByRole('button', { name: 'Link Sleeper Account' }))

    expect(await screen.findByText('Nope')).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('falls back to a helpful error mentioning the username', async () => {
    mockLinkSleeper.mockRejectedValue(new Error('boom'))
    renderLinkSleeper()

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'ghost')
    await userEvent.click(screen.getByRole('button', { name: 'Link Sleeper Account' }))

    expect(await screen.findByText(/Could not find Sleeper user "ghost"/)).toBeInTheDocument()
  })
})
