import request from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  password: string
  phone?: string
  nickname?: string
}

export interface LoginResult {
  token: string
  userId: number
  username: string
  nickname: string
  avatar: string
  role: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  phone: string
  avatar: string
  role: string
  healthProfile: string
  status: number
  createTime: string
  lastLogin: string
}

export const userApi = {
  login: (params: LoginParams): Promise<LoginResult> =>
    request.post('/user/login', params),

  register: (params: RegisterParams): Promise<void> =>
    request.post('/user/register', params),

  getUserInfo: (): Promise<UserInfo> =>
    request.get('/user/info'),

  updateUserInfo: (params: Partial<UserInfo>): Promise<void> =>
    request.put('/user/info', params),

  updateHealthProfile: (profile: string): Promise<void> =>
    request.put('/user/health-profile', profile, {
      headers: { 'Content-Type': 'text/plain' }
    }),

  listUsers: (params: { current: number; size: number; keyword?: string }): Promise<any> =>
    request.get('/user/list', { params }),

  updateUserStatus: (userId: number, status: number): Promise<void> =>
    request.put(`/user/${userId}/status`, null, { params: { status } }),

  updateUserRole: (userId: number, role: string): Promise<void> =>
    request.put(`/user/${userId}/role`, null, { params: { role } }),

  uploadAvatar: (file: File): Promise<string> => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/user/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  sendResetCode: (phone: string): Promise<void> =>
    request.post('/user/forgot-password/send-code', { phone }),

  resetPassword: (params: { phone: string; code: string; newPassword: string }): Promise<void> =>
    request.post('/user/forgot-password/reset', params)
}
