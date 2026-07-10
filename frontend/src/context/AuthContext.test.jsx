import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

// Mock the API module used by AuthContext.
vi.mock('../api/axiosClient', () => ({
  authApi: {
    me: vi.fn(),
    register: vi.fn(),
    login: vi.fn(),
    linkSleeper: vi.fn(),
    selectLeague: vi.fn(),
  },
}))

import { authApi } from '../api/axiosClient'
import { AuthProvider, useAuth } from './AuthContext'

// Small harness component that exposes context state + actions to the DOM.
function Harness() {
  const { user, loading, login, logout } = useAuth()
  return (
    <div>
      <span data-testid="loading">{String(loading)}</span>
      <span data-testid="user">{user ? user.displayName : 'none'}</span>
      <button onClick={() => login({ username: 'jdoe', password: 'pw' })}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  )
}

function renderWithProvider() {
  return render(
    <AuthProvider>
      <Harness />
    </AuthProvider>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('finishes loading with no user when there is no stored token', async () => {
    renderWithProvider()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(authApi.me).not.toHaveBeenCalled()
  })

  it('restores the session from a stored token', async () => {
    localStorage.setItem('token', 'abc')
    authApi.me.mockResolvedValue({ data: { displayName: 'Jane' } })

    renderWithProvider()

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Jane'))
    expect(authApi.me).toHaveBeenCalledOnce()
  })

  it('clears an invalid token when session restore fails', async () => {
    localStorage.setItem('token', 'bad')
    authApi.me.mockRejectedValue(new Error('401'))

    renderWithProvider()

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('logs in, stores the token, and sets the user', async () => {
    authApi.login.mockResolvedValue({ data: { token: 'jwt-123', displayName: 'Jane' } })
    renderWithProvider()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

    await userEvent.click(screen.getByText('login'))

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Jane'))
    expect(localStorage.getItem('token')).toBe('jwt-123')
    expect(authApi.login).toHaveBeenCalledWith({ username: 'jdoe', password: 'pw' })
  })

  it('logs out, clearing the token and user', async () => {
    authApi.login.mockResolvedValue({ data: { token: 'jwt-123', displayName: 'Jane' } })
    renderWithProvider()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

    await userEvent.click(screen.getByText('login'))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Jane'))

    await userEvent.click(screen.getByText('logout'))

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('throws if useAuth is used outside an AuthProvider', () => {
    // Suppress the expected React error boundary console noise.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    function Orphan() {
      useAuth()
      return null
    }
    expect(() => render(<Orphan />)).toThrow('useAuth must be used within an AuthProvider')
    spy.mockRestore()
  })
})
