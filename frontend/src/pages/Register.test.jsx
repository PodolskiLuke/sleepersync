import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

const mockNavigate = vi.fn()
const mockRegister = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ register: mockRegister }),
}))

import Register from './Register'

function renderRegister() {
  return render(
    <MemoryRouter>
      <Register />
    </MemoryRouter>
  )
}

async function fillAndSubmit() {
  await userEvent.type(screen.getByLabelText('Display Name'), 'Fantasy King')
  await userEvent.type(screen.getByLabelText('Email'), 'king@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Create Account' }))
}

describe('Register page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders all three fields and the setup steps', () => {
    renderRegister()
    expect(screen.getByLabelText('Display Name')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByText('Link your Sleeper username')).toBeInTheDocument()
  })

  it('registers and redirects to /link-sleeper on success', async () => {
    mockRegister.mockResolvedValue({ id: 1 })
    renderRegister()

    await fillAndSubmit()

    expect(mockRegister).toHaveBeenCalledWith({
      displayName: 'Fantasy King',
      email: 'king@example.com',
      password: 'password123',
    })
    expect(mockNavigate).toHaveBeenCalledWith('/link-sleeper')
  })

  it('shows the server error message when registration fails', async () => {
    mockRegister.mockRejectedValue({ response: { data: { message: 'Email taken' } } })
    renderRegister()

    await fillAndSubmit()

    expect(await screen.findByText('Email taken')).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('falls back to a generic error message', async () => {
    mockRegister.mockRejectedValue(new Error('boom'))
    renderRegister()

    await fillAndSubmit()

    expect(
      await screen.findByText('Registration failed. Please try again.')
    ).toBeInTheDocument()
  })
})
