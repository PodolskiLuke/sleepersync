import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await register(form)
      // After registration, user is logged in - go link their Sleeper account
      navigate('/link-sleeper')
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold">Create your account</h1>
          <p className="text-sleeper-muted mt-1 text-sm">
            Already have one?{' '}
            <Link to="/login" className="text-sleeper-accent hover:underline">
              Sign in
            </Link>
          </p>
        </div>

        {/* Setup steps hint */}
        <div className="card mb-6">
          <p className="text-xs text-sleeper-muted uppercase tracking-wider font-semibold mb-3">
            Setup steps
          </p>
          <div className="space-y-2">
            {[
              'Create your account',
              'Link your Sleeper username',
              'Select your league',
            ].map((step, i) => (
              <div key={i} className="flex items-center gap-3">
                <span className={`step-badge ${i === 0 ? '' : 'bg-sleeper-border text-sleeper-muted'}`}>
                  {i + 1}
                </span>
                <span className={`text-sm ${i === 0 ? 'text-white font-medium' : 'text-sleeper-muted'}`}>
                  {step}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Form */}
        <div className="card">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="displayName">Display Name</label>
              <input
                id="displayName"
                name="displayName"
                type="text"
                placeholder="e.g. Fantasy King"
                value={form.displayName}
                onChange={handleChange}
                required
              />
            </div>

            <div>
              <label htmlFor="email">Email</label>
              <input
                id="email"
                name="email"
                type="email"
                placeholder="you@example.com"
                value={form.email}
                onChange={handleChange}
                required
              />
            </div>

            <div>
              <label htmlFor="password">Password</label>
              <input
                id="password"
                name="password"
                type="password"
                placeholder="Minimum 8 characters"
                value={form.password}
                onChange={handleChange}
                required
                minLength={8}
              />
            </div>

            {error && <p className="error-msg">{error}</p>}

            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Creating account...' : 'Create Account'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
