
import client from './client'

export function register({
  username,
  email,
  password,
  fullName,
  phoneNumber,
  organization,
  accessReason,
}) {
  return client.post('/auth/register', {
    username,
    email,
    password,
    fullName,
    phoneNumber,
    organization,
    accessReason,
  })
}

export function login({ uniqueUserId, password }) {
  return client.post('/auth/login', {
    uniqueUserId,
    password,
  })
}
