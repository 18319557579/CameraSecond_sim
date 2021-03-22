bug：
1.
问题：原程序在第一次运行且完成授权之后，无法直接使用相机。

原因：在onResume()方法中，由于第一次需要进行if判断，所以无法进入打开相机的语句块，间接导致mCamera成员变量为空。但
    控件渲染依旧进行，surfaceChanged()中发送消息执行的“设置预览尺寸、设置预览画布、开启预览”这三个方法进行mCamera是
    否为空判断后，由于此时mCamera为空，因此这三个方法没起到作用。但由于授权后surfaceChanged()不再执行，因此无法预览
    成功。

解决：在onResume()方法else if语句块中的发送开启摄像头消息后，手动加一句
    cameraPreview.getHolder().setFormat(PREVIEW_FORMAT);

遗留问题：授权后预览虽然可以直接使用，但是手动加一句代码后，会依次回调销毁、创建、变化方法，并不是只回调变化的方法。
    surfaceDestroyed() 已回调surface销毁
    surfaceCreated() 已回调surface创建
    surfaceChanged() 已回调surface变化