import api from './api'

export interface FileInfo {
  id: string; filename: string; originalName: string;
  size: number; contentType: string; url: string;
}

export const fileService = {
  async upload(file: File): Promise<FileInfo> {
    const form = new FormData()
    form.append('file', file)
    const res = await api.post('/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
    return res.data as unknown as FileInfo
  },

  getUrl(fileId: string, ext?: string): string {
    const backend = localStorage.getItem('guglechat_backend_url') || ''
    const suffix = ext ? `.${ext}` : ''
    return backend ? `${backend}/api/files/${fileId}${suffix}` : `/api/files/${fileId}${suffix}`
  },
}
