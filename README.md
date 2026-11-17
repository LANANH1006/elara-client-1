# Elara Client

Mod Fabric phía client cho Minecraft Java 1.21.4: tối ưu hiệu năng, giao diện kiểu iOS và tiện ích.
Mod ID: `elaraclient`. Tác giả: Lan Anh.

## Đã làm

**Phase 1**: cấu trúc dự án, lưu cấu hình, màn hình khởi động, Dynamic Island hiện FPS, menu 5 tab.

**Phase 2**: Fix Lag cơ bản (giới hạn khoảng cách render, tiết kiệm GPU), kéo thả Dynamic Island, song ngữ Việt/English.

**Phase 2.5**
- Ẩn entity và particle xa hoặc bị tường che (`CullingManager`, mixin `EntityRenderDispatcherMixin` và `ParticleManagerMixin`). Người chơi khác luôn được vẽ.
- `FastMath`: bảng tra sin/cos cho code hiệu ứng của Elara.
- Nút **Dọn RAM** ở tab Fix Lag, gọi `System.gc()`, có thời gian chờ 15 giây.
- Restyle màn hình: mixin `ScreenMixin` (nền) và `ButtonWidgetMixin` (nút) để đổi mọi màn hình và nút thành kính mờ bo tròn kiểu iOS. Màn hình chính dùng `title_bg.png`.
- Splash screen vẽ quả táo cắn dở bằng code (`AppleLogo`), không dùng file ảnh.
- Không còn obfuscation: đã gỡ ProGuard để không làm gãy liên kết Mixin. File phát hành là jar remap chuẩn của Loom.
- Kiểm tra SHA-256 tên tác giả khi khởi động (`AuthorGuard`). Nếu tên bị sửa, mixin và menu Elara bị tắt.

## Trước khi build: đặt 2 ảnh

Chép 2 file này vào đúng đường dẫn (thư mục đã có sẵn):
- `src/main/resources/assets/elaraclient/textures/gui/title_bg.png`: hình nền sảnh chính
- `src/main/resources/assets/elaraclient/icon.png`: icon mod

Thiếu ảnh thì build vẫn chạy, nhưng màn hình chính và icon sẽ hiện ô lỗi.

## Build

Cần JDK 21.

- `./gradlew build`. File phát hành là `build/libs/elara-client-0.3.3.jar`, một file duy nhất đã remap.

## Giới hạn cần biết

- **Mã không bị làm khó đọc.** Ai decompile jar vẫn đọc được logic.
- **Khi mới vào game chỉ có phím Right Shift và nút [Elara].** Mọi tính năng (hiệu ứng, Dynamic Island, skin, Chat Touch) mặc định tắt. Bật từng cái trong menu Elara.
- **Lỗi ghi vào `config/elara/error.log`.** Tính năng nào lỗi thì bị tắt và stacktrace được ghi vào file đó. Menu Elara hiện dòng đỏ khi phiên đó có lỗi.
- **Kiểm tra tên tác giả không chống được người quyết tâm.** Họ có thể gỡ đoạn kiểm tra. Đây là lớp làm khó, không phải bảo vệ.
- **Mixin nhắm vào tên method của Yarn 1.21.4 viết theo trí nhớ.** Các inject đều có `require = 0`, nên method sai tên thì tính năng đó không chạy, game vẫn mở được. Nhưng accessor `ParticleAccessor` (đọc `x`, `y`, `z` của particle) mà sai tên sẽ làm game lỗi lúc khởi động. Nếu gặp, gửi log cho mình.
- **Restyle chưa được kiểm chứng trên game.** `TitleScreen` có thể override `renderBackground`, khi đó nền sảnh chính sẽ không đổi. Cần test và chỉnh.
- **Kính mờ là lớp bán trong suốt, không phải blur thật.**
- **Dọn RAM có thể làm game đứng vài trăm ms.**
- **Hình quả táo cắn dở là biểu tượng gần với logo Apple.** Nếu sau này phát hành công khai, nên đổi hình khác.

## Lộ trình

| Phase | Nội dung |
|-------|----------|
| 3 | Tab Hiệu ứng: mưa 3D, cánh ảo, vòng nhảy pixel, phụ kiện đầu, projectile trail |
| 4 | Tab Dao: hoạt ảnh cầm nắm và múa dao kiểu CS2, hoạt ảnh inventory |
| 5 | Tab Tâm ngắm: tâm ngắm pixel, vòng tầm đánh PvP, hiệu ứng đánh trúng, outline block |
| 6 | Tab Tiện ích: độ bền hotbar, keystrokes, trình phát nhạc, lời bài hát trên đảo |
| 7 | Tab Gói tài nguyên: chỉnh màu preset, đổi màu vật phẩm, pixel painter |
| 8 | Kết bạn, nhắn tin xuyên server, nút Join Server bạn bè |
