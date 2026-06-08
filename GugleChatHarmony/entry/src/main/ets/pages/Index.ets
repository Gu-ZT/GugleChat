import router from '@ohos.router';
import preferences from '@ohos.data.preferences';

@Entry
@Component
struct Index {
  @State ready: boolean = false;

  async aboutToAppear() {
    const prefs = await preferences.getPreferences(getContext(), 'guglechat');
    const token = await prefs.get('token', '') as string;
    if (token) {
      router.replaceUrl({ url: 'pages/MainPage' });
    } else {
      router.replaceUrl({ url: 'pages/LoginPage' });
    }
    this.ready = true;
  }

  build() {
    Column() {
      Text('GugleChat')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FFFFFF')
    }
    .width('100%').height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#313338')
  }
}
