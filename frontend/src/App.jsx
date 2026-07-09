import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Navbar from './components/Navbar'

import Register from './pages/Register'
import Login from './pages/Login'
import LinkSleeper from './pages/LinkSleeper'
import SelectLeague from './pages/SelectLeague'
import Dashboard from './pages/Dashboard'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Navbar />
        <Routes>
          {/* Public */}
          <Route path="/register" element={<Register />} />
          <Route path="/login" element={<Login />} />

          {/* Protected - requires JWT */}
          <Route element={<ProtectedRoute />}>
            {/* Step 3: Link Sleeper account (shown when sleeperUserId is missing) */}
            <Route path="/link-sleeper" element={<LinkSleeper />} />

            {/* Step 4: Select league (shown when activeLeagueId is missing) */}
            <Route path="/select-league" element={<SelectLeague />} />

            {/* Main app */}
            <Route path="/dashboard" element={<Dashboard />} />
          </Route>

          {/* Default redirect */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
