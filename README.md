# RCCamera
#利用外设设备录像录音，通过本地网络（蓝牙，WiFi）传给中心设备，中心设备推流至直播平台
#例如
可以将显示的 Android 设备作为受控远程摄像头，通过本地网络（蓝牙，WiFi）将音视频传给控制设备（例如 Android手机），实现本地网络监控。

#起源
Meta 的 Ray-Ban 智能眼镜亲身体验：可以直播到 Instagram
https://www.bilibili.com/video/BV19N4y1Z77d/?spm_id_from=333.337.search-card.all.click
使用场景是：
智能手机作为控制端，可以通过蓝牙发起直命令给智能眼镜，智能眼镜作为外设进行录音录像，并将音视频通过蓝牙回传给智能手机，智能手机将音视频打包封装通过直播协议分发至直播平台。

Meta Ray-Ban经过调研，控制和影音数据传输均使用蓝牙，因此直播码率在 600-2000kbps

本项目控制部分仍然使用蓝牙（Gatt）， 影音数据除了蓝牙外增加了 WIFI和 WIFI P2P 的支持，以便实现更高码率的音视频传输。

#工程分外围设备（远程受控端）和 中心设备（控制端），概念来自蓝牙规范
# 中心设备-控制端
1 控制外围设备（主要是：录音录像)
    a. 控制命令通道：蓝牙Gatt协议
2 接收外围设备的影音数据并在中心设备渲染播放
    a. 传输音视频数据通道：蓝牙SPP,蓝牙 Gatt Over BR/EDR, WIFI，WIFI P2P
3 将接收到的影音数据进行封装，通过RTMP 协议上传至直播平台（Douyin)
4 实现了 Douyin RTMP 的接入，使用的测试 key，这里不方便透露，感兴趣的可以参考
https://developer.open-douyin.com/docs/resource/zh-CN/dop/ability/douyin-live-sdk/douyin-live-sdk/android
5 如需分发至其他直播平台，需要修改下对应的直播平台的RTMP上传url

#外围设备-远程端
主要功能
1 通过设备摄像头录音录像
2 将影音数据按特定协议发送给中心设备
