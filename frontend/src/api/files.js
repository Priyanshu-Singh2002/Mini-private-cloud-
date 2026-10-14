
import client from './client'

export function getFiles() {
  return client.get('/files')
}

export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)

  return client.post('/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function downloadFile(fileId) {
  return client.get(`/files/${fileId}/download`, {
    responseType: 'blob',
  })
}

export function deleteFile(fileId) {
  return client.delete(`/files/${fileId}`)
}
