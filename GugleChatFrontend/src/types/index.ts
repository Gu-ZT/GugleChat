export interface UserInfo {
  id: number; username: string; email: string; nickname: string; avatarUrl: string | null;
}
export interface AuthResponse {
  token: string; tokenType: string; user: UserInfo;
}
export type ChannelType = 'TEXT' | 'VOICE'
export interface Channel {
  id: number; name: string; description: string | null; type: ChannelType;
  createdBy: number; memberCount: number; joined: boolean;
  createdAt: string; updatedAt: string;
}
export interface ChannelMember {
  id: number; userId: number; role: 'OWNER' | 'ADMIN' | 'MEMBER'; joinedAt: string;
}
export type MessageType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO' | 'FILE' | 'LINK_EMBED'
export interface Message {
  id: number; channelId: number; userId: number; username: string;
  content: string; type: MessageType; parentId: number | null;
  fileIds: number[] | null; editedAt: string | null; createdAt: string;
}
export interface ApiResponse<T> { code: number; message: string; data: T; }
