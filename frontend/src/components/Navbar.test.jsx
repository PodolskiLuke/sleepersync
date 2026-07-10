import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

const mockNavigate = vi.fn()
const mockLogout = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../context/AuthContext'
import Navbar from './Navbar'

function renderNavbar() {
  return render(
    <MemoryRouter>
      <Navbar />
    </MemoryRouter>
  )
}

describe('Navbar', () => {
  it('shows Login and Register links for guests', () => {
    useAuth.mockReturnValue({ user: null, logout: mockLogout })
    renderNavbar()

    expect(screen.getByRole('link', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Register' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Logout' })).not.toBeInTheDocument()
  })

  it('shows the display name and Logout for authenticated users', () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', activeLeagueId: null },
      logout: mockLogout,
    })
    renderNavbar()

    expect(screen.getByText('Jane')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Logout' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Login' })).not.toBeInTheDocument()
  })

  it('shows the Switch League link only when a league is active', () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', activeLeagueId: 'L1' },
      logout: mockLogout,
    })
    renderNavbar()
    expect(screen.getByRole('link', { name: 'Switch League' })).toBeInTheDocument()
  })

  it('logs out and navigates to /login when Logout is clicked', async () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', activeLeagueId: 'L1' },
      logout: mockLogout,
    })
    renderNavbar()

    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))

    expect(mockLogout).toHaveBeenCalledOnce()
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })
})
