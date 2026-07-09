import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Wraps protected routes.
 * - Not logged in → redirect to /login
 * - Logged in but no Sleeper linked → redirect to /link-sleeper
 * - Sleeper linked but no league selected → redirect to /select-league
 * - All good → render the child route
 *
 * Note: each check skips redirecting when already on the target route,
 * otherwise React Router would repeatedly navigate to the same location
 * (infinite redirect loop -> blank screen / "Maximum update depth exceeded").
 */
export default function ProtectedRoute() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="w-8 h-8 border-4 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />

  if (!user.sleeperUserId && location.pathname !== '/link-sleeper') {
    return <Navigate to="/link-sleeper" replace />
  }

  if (user.sleeperUserId && !user.activeLeagueId && location.pathname !== '/select-league') {
    return <Navigate to="/select-league" replace />
  }

  return <Outlet />
}
