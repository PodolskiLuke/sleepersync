import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { authApi } from '../api/axiosClient'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)       // current user profile
  const [loading, setLoading] = useState(true) // true while restoring session

  // Restore session on page load if a token exists in localStorage
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) {
      setLoading(false)
      return
    }
    authApi.me()
      .then((res) => setUser(res.data))
      .catch(() => localStorage.removeItem('token'))
      .finally(() => setLoading(false))
  }, [])

  const saveSession = (data) => {
    localStorage.setItem('token', data.token)
    setUser(data)
  }

  const register = useCallback(async (formData) => {
    const res = await authApi.register(formData)
    saveSession(res.data)
    return res.data
  }, [])

  const login = useCallback(async (formData) => {
    const res = await authApi.login(formData)
    saveSession(res.data)
    return res.data
  }, [])

  const linkSleeper = useCallback(async (sleeperUsername) => {
    const res = await authApi.linkSleeper({ sleeperUsername })
    // Update stored token + user state with the refreshed data
    saveSession(res.data)
    return res.data
  }, [])

  const selectLeague = useCallback(async (leagueId) => {
    const res = await authApi.selectLeague({ leagueId })
    saveSession(res.data)
    return res.data
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, register, login, linkSleeper, selectLeague, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
