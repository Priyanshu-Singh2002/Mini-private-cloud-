import client from './client'

export function submitJob(payload) {
  return client.post('/jobs', payload)
}

export function getMyJobs() {
  return client.get('/jobs')
}

export function getJob(jobId) {
  return client.get(`/jobs/${jobId}`)
}

export function cancelJob(jobId) {
  return client.post(`/jobs/${jobId}/cancel`)
}

export function getAdminJobs() {
  return client.get('/admin/jobs')
}
