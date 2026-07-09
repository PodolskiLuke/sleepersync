import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { leaguesApi } from '../api/axiosClient'

const FEATURES = [
  {
    title: 'Start / Sit Advisor',
    description: 'Get weekly lineup recommendations based on matchups and recent performance.',
    icon: '🪑',
    available: false,
  },
  {
    title: 'Trade Analyzer',
    description: 'Evaluate trades using dynasty and redraft values, age curves, and positional scarcity.',
    icon: '🔄',
    available: false,
  },
  {
    title: 'Draft Helper',
    description: 'Connect to a live draft to track your picks and see the best available players by position.',
    icon: '📋',
    available: true,
    path: '/draft-helper',
  },
  {
    title: 'Player Rankings',
    description: 'Dynasty and redraft rankings updated with the latest performance data.',
    icon: '📊',
    available: false,
  },
]

export default function Dashboard() {
  const { user } = useAuth()
  const [league, setLeague] = useState(null)
  const [rosters, setRosters] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!user?.activeLeagueId) return
    Promise.all([
      leaguesApi.getLeague(user.activeLeagueId),
      leaguesApi.getRosters(user.activeLeagueId),
    ])
      .then(([leagueRes, rosterRes]) => {
        setLeague(leagueRes.data)
        setRosters(rosterRes.data)
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [user?.activeLeagueId])

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      {/* Welcome header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold">
          Welcome back, {user?.displayName} 👋
        </h1>
        <p className="text-sleeper-muted text-sm mt-1">
          Sleeper:{' '}
          <span className="text-sleeper-accent font-medium">@{user?.sleeperUsername}</span>
        </p>
      </div>

      {/* Active League Card */}
      {loading ? (
        <div className="flex justify-center py-12">
          <div className="w-8 h-8 border-4 border-sleeper-accent border-t-transparent rounded-full animate-spin" />
        </div>
      ) : league ? (
        <div className="card mb-8">
          <div className="flex items-start justify-between flex-wrap gap-4">
            <div>
              <p className="text-xs text-sleeper-muted uppercase tracking-wider font-semibold mb-1">
                Active League
              </p>
              <h2 className="text-xl font-bold">{league.name}</h2>
              <div className="flex gap-3 mt-1.5 text-sm text-sleeper-muted">
                <span>{league.total_rosters ?? league.totalRosters} teams</span>
                <span>·</span>
                <span>{league.season} {league.season_type ?? league.seasonType}</span>
                <span>·</span>
                <span
                  className={`font-medium ${
                    league.status === 'in_season' ? 'text-sleeper-green' : ''
                  }`}
                >
                  {league.status?.replace(/_/g, ' ')}
                </span>
              </div>
            </div>
            <div className="text-right">
              <p className="text-xs text-sleeper-muted mb-1">Rosters synced</p>
              <p className="text-2xl font-bold text-sleeper-green">{rosters.length}</p>
            </div>
          </div>
        </div>
      ) : null}

      {/* Feature cards */}
      <div>
        <h2 className="text-lg font-semibold mb-4">Tools</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {FEATURES.map((feature) => {
            const CardTag = feature.available ? Link : 'div'
            return (
              <CardTag
                key={feature.title}
                {...(feature.available ? { to: feature.path } : {})}
                className={`card relative block ${
                  feature.available
                    ? 'hover:border-sleeper-accent cursor-pointer transition-colors'
                    : 'opacity-60'
                }`}
              >
                {!feature.available && (
                  <span className="absolute top-4 right-4 text-xs bg-sleeper-border text-sleeper-muted px-2 py-0.5 rounded-full">
                    Coming soon
                  </span>
                )}
                <div className="text-2xl mb-2">{feature.icon}</div>
                <h3 className="font-semibold mb-1">{feature.title}</h3>
                <p className="text-sm text-sleeper-muted">{feature.description}</p>
              </CardTag>
            )
          })}
        </div>
      </div>
    </div>
  )
}
