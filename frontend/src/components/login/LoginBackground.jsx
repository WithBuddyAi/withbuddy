// 데스크탑 전체 배경 (그라디언트 + 점 패턴)
function LoginBackground() {
  return (
    <div className="hidden lg:block fixed inset-0 pointer-events-none z-0">
      {/* 우측 연하늘색 그라디언트 */}
      <div
        className="absolute top-0 right-0 h-full w-full"
        style={{
          background:
            "linear-gradient(to left, #EAF5FF 0%, #EFF8FF 20%, #F3FAFF 50%, #F9FCFF 75%, #FFFFFF 100%)",
        }}
      />
      {/* 점 패턴 전체 배경 */}
      <svg className="absolute inset-0 opacity-20 h-full w-1/2">
        <defs>
          <pattern
            id="dots"
            x="0"
            y="0"
            width="24"
            height="24"
            patternUnits="userSpaceOnUse"
          >
            <circle cx="2" cy="2" r="1.6" fill="#4791CA99" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#dots)" />
      </svg>
    </div>
  );
}

export default LoginBackground;
