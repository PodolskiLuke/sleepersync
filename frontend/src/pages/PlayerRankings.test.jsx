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
  {
    playerId: '3',
    playerName: 'Klay Thompson',
    position: 'SF',
    eligiblePositions: 'PF/SF/SG',
    team: 'DAL',
    rookie: false,
    sleeperFantasyPtsAvg: 19.1,
    sleeperSearchRank: 119,
    externalAvgRank: 18.0,
    externalRankCount: 2,
    blendedScore: 0.71,
    finalRank: 3,
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
    expect(screen.getByText('How rankings work')).toBeInTheDocument()
    expect(screen.getByText(/combines Sleeper market value, recent production, and external rankings/i)).toBeInTheDocument()
    expect(screen.getByText(/Sleeper value rank\/search rank\. Lower is better/i)).toBeInTheDocument()
    expect(screen.getByText(/intentionally tempered versus proven veterans on the full board/i)).toBeInTheDocument()
  })

  it('loads and displays player rankings', async () => {
    rankingsApi.getAllRankings.mockResolvedValue({ data: MOCK_RANKINGS })
    renderPlayerRankings()

    expect(await screen.findByText('Luka Doncic')).toBeInTheDocument()
    expect(screen.getByText('Jayson Tatum')).toBeInTheDocument()
    expect(screen.getByText('PF/SF/SG')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ALL (3)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'PG (1)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'SF (2)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'PF (1)' })).toBeInTheDocument()
  })

  it('filters by multi-position eligibility', async () => {
    rankingsApi.getAllRankings.mockResolvedValue({ data: MOCK_RANKINGS })
    renderPlayerRankings()

    expect(await screen.findByText('Klay Thompson')).toBeInTheDocument()
    await screen.findByText('PF/SF/SG')
    await screen.getByRole('button', { name: 'PF' }).click()

    expect(screen.getByText('Klay Thompson')).toBeInTheDocument()
  })

  it('displays error message on API failure', async () => {
    rankingsApi.getAllRankings.mockRejectedValue(new Error('API Error'))
    renderPlayerRankings()

    expect(await screen.findByText('Failed to load player rankings')).toBeInTheDocument()
  })
})
