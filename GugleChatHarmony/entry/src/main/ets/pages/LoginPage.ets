import router from '@ohos.router';
import http from '@ohos.net.http';
import promptAction from '@ohos.promptAction';
import preferences from '@ohos.data.preferences';

@Entry
@Component
struct LoginPage {
  @State username: string = '';
  @State password: string = '';
  @State backendUrl: string = '';

  async aboutToAppear() {
    const prefs = await preferences.getPreferences(getContext(), 'guglechat');
    this.backendUrl = await prefs.get('backend_url', '') as string;
  }

  async login() {
    const url = (this.backendUrl || 'http://10.0.2.2:8080') + '/api/auth/login';
    const req = http.createHttp();
    try {
      const res = await req.request(url, {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/json' },
        extraData: JSON.stringify({ username: this.username, password: this.password })
      });
      const data = JSON.parse(res.result as string);
      if (data.code === 200) {
        const prefs = await preferences.getPreferences(getContext(), 'guglechat');
        await prefs.put('token', data.data.token);
        await prefs.flush();
        router.pushUrl({ url: 'pages/MainPage' });
      } else {
        promptAction.showToast({ message: data.message || 'Login failed' });
      }
    } catch (e) {
      promptAction.showToast({ message: 'Network error' });
    } finally { req.destroy(); }
  }

  build() {
    Column() {
      Text('GugleChat').fontSize(28).fontWeight(FontWeight.Bold).margin({ bottom: 32 })
      TextInput({ placeholder: 'Username', text: this.username })
        .onChange((v: string) => { this.username = v }).width('100%').margin({ bottom: 8 })
      TextInput({ placeholder: 'Password', text: this.password })
        .type(InputType.Password).onChange((v: string) => { this.password = v })
        .width('100%').margin({ bottom: 16 })
      Button('Sign In').width('100%').onClick(() => { this.login() })
    }
    .width('100%').height('100%').justifyContent(FlexAlign.Center)
    .padding(32).backgroundColor('#313338')
  }
}
