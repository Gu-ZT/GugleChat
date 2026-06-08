import router from '@ohos.router';
import http from '@ohos.net.http';
import webSocket from '@ohos.net.webSocket';
import preferences from '@ohos.data.preferences';

interface Channel { id: number; name: string; type: string; joined: boolean; memberCount: number; }
interface Message { id: number; channelId: number; userId: number; username: string; content: string; type: string; }

@Entry
@Component
struct MainPage {
  @State channels: Channel[] = [];
  @State messages: Message[] = [];
  @State currentChannel: Channel | null = null;
  @State input: string = '';
  private token: string = '';
  private backendUrl: string = '';
  private ws: webSocket.WebSocket | null = null;

  async aboutToAppear() {
    const prefs = await preferences.getPreferences(getContext(), 'guglechat');
    this.token = await prefs.get('token', '') as string;
    this.backendUrl = await prefs.get('backend_url', '') as string;
    await this.loadChannels();
    this.connectWs();
  }

  get baseUrl(): string { return this.backendUrl || 'http://10.0.2.2:8080'; }

  async loadChannels() {
    const req = http.createHttp();
    try {
      const res = await req.request(this.baseUrl + '/api/channels', {
        header: { 'Authorization': 'Bearer ' + this.token }
      });
      const data = JSON.parse(res.result as string);
      if (data.code === 200) this.channels = data.data as Channel[];
    } catch (_) {} finally { req.destroy(); }
  }

  async selectChannel(ch: Channel) {
    this.currentChannel = ch;
    const req = http.createHttp();
    try {
      const res = await req.request(`${this.baseUrl}/api/channels/${ch.id}/messages`, {
        header: { 'Authorization': 'Bearer ' + this.token }
      });
      const data = JSON.parse(res.result as string);
      if (data.code === 200) this.messages = (data.data as Message[]).reverse();
    } catch (_) {} finally { req.destroy(); }
    // Subscribe via WS
    const wsUrl = this.baseUrl.replace('http', 'ws') + '/ws?token=' + this.token;
    this.ws?.send('SUBSCRIBE\ndestination:/topic/channel.' + ch.id + '\nid:sub-' + ch.id + '\n\n\0');
  }

  connectWs() {
    this.ws = webSocket.createWebSocket();
    const wsUrl = this.baseUrl.replace('http', 'ws') + '/ws?token=' + this.token;
    this.ws.connect(wsUrl, (err, value) => {
      if (!err) {
        // Send STOMP CONNECT then subscribe to heartbeat
        this.ws?.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n\0');
      }
    });
    this.ws.on('message', (err, value) => {
      const text = value as string;
      if (text.includes('MESSAGE')) {
        const idx = text.indexOf('\n\n');
        if (idx > 0) {
          const body = text.substring(idx + 2).replace(/\0/g, '');
          try { this.messages = [...this.messages, JSON.parse(body) as Message]; } catch (_) {}
        }
      }
    });
  }

  sendMessage() {
    if (!this.currentChannel || !this.input.trim() || !this.ws) return;
    const body = JSON.stringify({ content: this.input.trim(), type: 'TEXT' });
    this.ws.send(`SEND\ndestination:/app/chat.send/${this.currentChannel.id}\ncontent-type:application/json\n\n${body}\0`);
    this.input = '';
  }

  logout() {
    this.ws?.close();
    router.replaceUrl({ url: 'pages/LoginPage' });
  }

  build() {
    Row() {
      // Sidebar
      Column() {
        Text('GugleChat').fontSize(18).fontColor(Color.White).padding(12)
        List() {
          ForEach(this.channels, (ch: Channel) => {
            ListItem() {
              Text((ch.type === 'VOICE' ? '🔊 ' : '# ') + ch.name)
                .fontColor(this.currentChannel?.id === ch.id ? '#FFFFFF' : '#949BA4')
                .padding(8)
            }.onClick(() => { this.selectChannel(ch) })
          })
        }.layoutWeight(1).backgroundColor('#1E1F22')
        Button('Logout').onClick(() => { this.logout() }).margin(8)
      }.width(240).height('100%').backgroundColor('#1E1F22')

      // Chat
      Column() {
        if (this.currentChannel) {
          Text((this.currentChannel.type === 'VOICE' ? '🔊 ' : '# ') + this.currentChannel.name)
            .fontColor(Color.White).fontSize(16).padding(16)
          List() {
            ForEach(this.messages, (msg: Message) => {
              ListItem() {
                Column() {
                  Text(msg.username).fontColor('#80B4FF').fontSize(12)
                  Text(msg.content).fontColor('#DBDEE1')
                }.padding(4)
              }
            })
          }.layoutWeight(1).padding(8)
          Row() {
            TextInput({ placeholder: 'Message', text: this.input })
              .onChange((v: string) => { this.input = v }).layoutWeight(1)
            Button('Send').onClick(() => { this.sendMessage() })
          }.padding(12).width('100%')
        } else {
          Text('Select a channel').fontColor('#949BA4')
            .width('100%').textAlign(TextAlign.Center).margin({ top: 200 })
        }
      }.layoutWeight(1).height('100%').backgroundColor('#313338')
    }.width('100%').height('100%')
  }
}
