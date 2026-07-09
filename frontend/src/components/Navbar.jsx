import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <nav className="border-b border-sleeper-border bg-sleeper-card">
      <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
        {/* Brand */}
        <Link to="/dashboard" className="text-sleeper-accent font-bold text-lg tracking-tight">
          SleeperSync
        </Link>

        {/* Right side */}
        {user ? (
          <div className="flex items-center gap-4">
            <span className="text-sleeper-muted text-sm hidden sm:block">
              {user.displayName}
            </span>
            {user.activeLeagueId && (
              <Link
                to="/select-league"
                className="text-xs text-sleeper-muted hover:text-white transition"
              >
                Switch League
              </Link>
            )}
            <button
              onClick={handleLogout}
              className="text-sm text-sleeper-muted hover:text-sleeper-red transition"
            >
              Logout
            </button>
          </div>
        ) : (
          <div className="flex gap-3 text-sm">
            <Link to="/login" className="text-sleeper-muted hover:text-white transition">
              Login
            </Link>
            <Link to="/register" className="text-sleeper-accent hover:text-blue-300 transition">
              Register
            </Link>
          </div>
        )}
      </div>
    </nav>
  )
}
