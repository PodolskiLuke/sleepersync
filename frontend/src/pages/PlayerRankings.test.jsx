import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('../api/axiosClient', () => ({
  rankingsApi: {
    getAllRankings: vi.fn(),
  },
}))

import { rankingsApi } from '../api/axiosClient'
import PlayerRankings from './PlayerRankings'

const MOCK_RANKINGS = [
  {
    playerId: '1',
    playerName: 'Luka Doncic',
    position: 'PG',
    team: 'DAL',
    rookie: false,
    sleeperFantasyPtsAvg: 52.3,
    sleeperSearchRank: 1,
    externalAvgRank: 1.5,
    externalRankCount: 2,
    blendedScore: 0.95,
    finalRank: 1,
    sourceRanks: [],
  },
  {
    playerId: '2',
    playerName: 'Jayson Tatum',
    position: 'SF',
    team: 'BOS',
    rookie: false,
    sleeperFantasyPtsAvg: 48.1,
    sleeperSearchRank: 2,
    externalAvgRank: 2.0,
    externalRankCount: 2,
    blendedScore: 0.92,
    finalRank: 2,
    sourceRanks: [],
  },
]

function renderPlayerRankings() {
  return render(
    <MemoryRouter>
      <PlayerRankings />
    </MemoryRouter>
  )
}

describe('PlayerRankings page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('displays the page title and description', async () => {
    rankingsApi.getAllRankings.mockResolvedValue({ data: MOCK_RANKINGS })
    renderPlayerRankings()
    expect(screen.getByText('Player Rankings')).toBeInTheDocument()
    expect(screen.getByText(/Dynasty and redraft rankings/)).toBeInTheDocument()
  })

  it('loads and displays player rankings', async () => {
    rankingsApi.getAllRankings.mockResolvedValue({ data: MOCK_RANKINGS })
    renderPlayerRankings()

    expect(await screen.findByText('Luka Doncic')).toBeInTheDocument()
    expect(screen.getByText('Jayson Tatum')).toBeInTheDocument()
  })

  it('displays error message on API failure', async () => {
    rankingsApi.getAllRankings.mockRejectedValue(new Error('API Error'))
    renderPlayerRankings()

    expect(await screen.findByText('Failed to load player rankings')).toBeInTheDocument()
  })
})
