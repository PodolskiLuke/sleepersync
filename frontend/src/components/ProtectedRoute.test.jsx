import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'

// Mock useAuth so we can drive ProtectedRoute through each state.
vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../context/AuthContext'
import ProtectedRoute from './ProtectedRoute'

// Renders ProtectedRoute at `initialPath` with labelled destination routes.
function renderAt(initialPath) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<div>Dashboard Page</div>} />
          <Route path="/link-sleeper" element={<div>Link Sleeper Page</div>} />
          <Route path="/select-league" element={<div>Select League Page</div>} />
        </Route>
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  it('shows a loading spinner while the session is restoring', () => {
    useAuth.mockReturnValue({ user: null, loading: true })
    const { container } = renderAt('/dashboard')
    expect(container.querySelector('.animate-spin')).toBeInTheDocument()
  })

  it('redirects unauthenticated users to /login', () => {
    useAuth.mockReturnValue({ user: null, loading: false })
    renderAt('/dashboard')
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('redirects to /link-sleeper when no Sleeper account is linked', () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', sleeperUserId: null, activeLeagueId: null },
      loading: false,
    })
    renderAt('/dashboard')
    expect(screen.getByText('Link Sleeper Page')).toBeInTheDocument()
  })

  it('redirects to /select-league when linked but no league is active', () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', sleeperUserId: 'U1', activeLeagueId: null },
      loading: false,
    })
    renderAt('/dashboard')
    expect(screen.getByText('Select League Page')).toBeInTheDocument()
  })

  it('renders the child route when the user is fully set up', () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', sleeperUserId: 'U1', activeLeagueId: 'L1' },
      loading: false,
    })
    renderAt('/dashboard')
    expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
  })

  it('does not redirect away from /link-sleeper when already there', () => {
    useAuth.mockReturnValue({
      user: { displayName: 'Jane', sleeperUserId: null, activeLeagueId: null },
      loading: false,
    })
    renderAt('/link-sleeper')
    expect(screen.getByText('Link Sleeper Page')).toBeInTheDocument()
  })
})
