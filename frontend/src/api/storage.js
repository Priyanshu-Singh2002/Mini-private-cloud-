import client from './client'

export function getMyStorageUsage() {
  return client.get('/storage/me')
}

export function updateUserQuota(userId, storageQuotaBytes) {
  return client.patch(`/admin/users/${userId}/quota`, {
    storageQuotaBytes,
  })
}
