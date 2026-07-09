import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function LinkSleeper() {
  const { user, linkSleeper } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await linkSleeper(username.trim())
      navigate('/select-league')
    } catch (err) {
      setError(
        err.response?.data?.message ||
        `Could not find Sleeper user "${username}". Please check the username and try again.`
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {/* Progress indicator */}
        <div className="flex items-center gap-2 mb-8 justify-center">
          {[1, 2, 3].map((n) => (
            <div key={n} className="flex items-center gap-2">
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
                  ${n === 1 ? 'bg-sleeper-green text-sleeper-bg' : ''}
                  ${n === 2 ? 'bg-sleeper-accent text-sleeper-bg' : ''}
                  ${n === 3 ? 'bg-sleeper-border text-sleeper-muted' : ''}`}
              >
                {n === 1 ? '✓' : n}
              </div>
              {n < 3 && <div className="w-10 h-px bg-sleeper-border" />}
            </div>
          ))}
        </div>

        {/* Header */}
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold">Link your Sleeper account</h1>
          <p className="text-sleeper-muted mt-1 text-sm">
            Hi {user?.displayName}! Enter your Sleeper username to sync your leagues.
          </p>
        </div>

        <div className="card">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="sleeperUsername">Sleeper Username</label>
              <input
                id="sleeperUsername"
                type="text"
                placeholder="e.g. hoopsdynasty"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
              <p className="text-xs text-sleeper-muted mt-1.5">
                Find your username in the Sleeper app under your profile.
              </p>
            </div>

            {error && <p className="error-msg">{error}</p>}

            <button type="submit" className="btn-primary" disabled={loading || !username.trim()}>
              {loading ? 'Verifying...' : 'Link Sleeper Account'}
            </button>
          </form>
        </div>

        {/* Info box */}
        <div className="mt-4 p-4 rounded-lg border border-sleeper-border bg-sleeper-card/50 text-sm text-sleeper-muted">
          <p className="font-medium text-white mb-1">Why do we need this?</p>
          <p>
            SleeperSync uses your Sleeper username to pull your leagues, rosters, and matchups
            directly from the Sleeper API — no manual data entry needed.
          </p>
        </div>
      </div>
    </div>
  )
}
