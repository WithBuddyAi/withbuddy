import axios from "axios"

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
})

axiosInstance.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('accessToken')

    if (accessToken) {
      config.headers['Authorization'] = `Bearer ${accessToken}`
    }

    return config
  }
)

axiosInstance.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
        if (error.response?.status === 401) {
      const code = error.response?.data?.code
      if (code === 'TOKEN_MISSING' || 
          code === 'TOKEN_EXPIRED' || 
          code === 'INVALID_TOKEN' || 
          code === 'USER_NOT_FOUND'
        ) {
          localStorage.removeItem('accessToken')
          localStorage.removeItem('dayCount')
          localStorage.removeItem('hireDate')
          localStorage.removeItem('name')
          window.location.href = '/login'
        return Promise.reject(error)
      }
    }
    return Promise.reject(error)
  }
)

export default axiosInstance