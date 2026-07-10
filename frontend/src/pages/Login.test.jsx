import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

const mockNavigate = vi.fn()
const mockLogin = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin }),
}))

import Login from './Login'

function renderLogin() {
  return render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>
  )
}

// Fills the email + password fields and submits the form.
async function fillAndSubmit(email = 'jane@example.com', password = 'secret') {
  await userEvent.type(screen.getByLabelText('Email'), email)
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Sign In' }))
}

describe('Login page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the email and password fields', () => {
    renderLogin()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
  })

  it('submits credentials and navigates to /link-sleeper when no Sleeper linked', async () => {
    mockLogin.mockResolvedValue({ sleeperUserId: null, activeLeagueId: null })
    renderLogin()

    await fillAndSubmit()

    expect(mockLogin).toHaveBeenCalledWith({
      email: 'jane@example.com',
      password: 'secret',
    })
    expect(mockNavigate).toHaveBeenCalledWith('/link-sleeper')
  })

  it('navigates to /select-league when linked but no league is active', async () => {
    mockLogin.mockResolvedValue({ sleeperUserId: 'U1', activeLeagueId: null })
    renderLogin()

    await fillAndSubmit()

    expect(mockNavigate).toHaveBeenCalledWith('/select-league')
  })

  it('navigates to /dashboard for a fully set-up user', async () => {
    mockLogin.mockResolvedValue({ sleeperUserId: 'U1', activeLeagueId: 'L1' })
    renderLogin()

    await fillAndSubmit()

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
  })

  it('shows the server error message when login fails', async () => {
    mockLogin.mockRejectedValue({ response: { data: { message: 'Bad creds' } } })
    renderLogin()

    await fillAndSubmit()

    expect(await screen.findByText('Bad creds')).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('shows a generic error message when the server gives none', async () => {
    mockLogin.mockRejectedValue(new Error('network'))
    renderLogin()

    await fillAndSubmit()

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument()
  })
})
