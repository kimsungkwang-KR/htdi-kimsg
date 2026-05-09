# HTDI MES Android Tablet App v74

PWA/Chrome 주소창 문제를 우회하고, 실제 산업용 태블릿 앱처럼 실행하는 Android WebView APK 프로젝트입니다.

## 기능
- 서버 URL 직접 입력
- 서버 URL 저장
- 다음 실행부터 자동 접속
- 연결 테스트
- 전체화면 키오스크 모드
- 가로모드 고정
- 화면 꺼짐 방지
- 뒤로가기 제한
- HTTPS 로컬 인증서 WebView 허용

## Android Studio 빌드
1. Android Studio 실행
2. 이 폴더 열기
3. Gradle Sync
4. Build > Build Bundle(s) / APK(s) > Build APK(s)
5. app/build/outputs/apk/debug/app-debug.apk 설치

## 서버 주소 예시
https://192.168.0.163:4443

개발 테스트:
http://192.168.0.163:5173
