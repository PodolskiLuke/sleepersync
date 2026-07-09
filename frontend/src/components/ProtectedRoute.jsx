import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Wraps protected routes.
 * - Not logged in → redirect to /login
 * - Logged in but no Sleeper linked → redirect to /link-sleeper
 * - Sleeper linked but no league selected → redirect to /select-league
 * - All good → render the child route
 */
export default function ProtectedRoute() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="w-8 h-8 border-4 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />
  if (!user.sleeperUserId) return <Navigate to="/link-sleeper" replace />
  if (!user.activeLeagueId) return <Navigate to="/select-league" replace />

  return <Outlet />
}
