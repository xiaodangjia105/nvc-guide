import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import Layout from './components/Layout';
import { Suspense, lazy } from 'react';
import type { UploadKnowledgeBaseResponse } from './api/knowledgebase';
import { ROUTES } from './constants/routes';

// Lazy load - 知识库页面
const KnowledgeBaseQueryPage = lazy(() => import('./pages/KnowledgeBaseQueryPage'));
const KnowledgeBaseUploadPage = lazy(() => import('./pages/KnowledgeBaseUploadPage'));
const KnowledgeBaseManagePage = lazy(() => import('./pages/KnowledgeBaseManagePage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));

// Lazy load - NVC 页面
const NvcPracticeHubPage = lazy(() => import('./pages/NvcPracticeHubPage'));
const NvcPracticePage = lazy(() => import('./pages/NvcPracticePage'));
const NvcVoicePage = lazy(() => import('./pages/NvcVoicePage'));
const NvcHistoryPage = lazy(() => import('./pages/NvcHistoryPage'));
const NvcReportPage = lazy(() => import('./pages/NvcReportPage'));
const NvcProfilePage = lazy(() => import('./pages/NvcProfilePage'));
const NvcDashboardPage = lazy(() => import('./pages/NvcDashboardPage'));
const NvcScenarioLibraryPage = lazy(() => import('./pages/NvcScenarioLibraryPage'));
const NvcAgentConfigPage = lazy(() => import('./pages/NvcAgentConfigPage'));
const NvcWikiPage = lazy(() => import('./pages/NvcWikiPage'));

// Loading component
const Loading = () => (
  <div className="flex items-center justify-center min-h-[50vh]">
    <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
  </div>
);

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route path="/" element={<Layout />}>
            {/* 默认重定向到 NVC 练习中心 */}
            <Route index element={<Navigate to="/nvc" replace />} />

            {/* NVC 练习 */}
            <Route path="nvc" element={<NvcPracticeHubPage />} />
            <Route path="nvc/practice/:sessionId" element={<NvcPracticePage />} />
            <Route path="nvc/voice/:sessionId" element={<NvcVoicePage />} />
            <Route path="nvc/history" element={<NvcHistoryPage />} />
            <Route path="nvc/history/:sessionId/report" element={<NvcReportPage />} />
            <Route path="nvc/profile" element={<NvcProfilePage />} />
            <Route path="nvc/dashboard" element={<NvcDashboardPage />} />
            <Route path="nvc/scenarios" element={<NvcScenarioLibraryPage />} />
            <Route path="nvc/agents" element={<NvcAgentConfigPage />} />
            <Route path="nvc/wiki" element={<NvcWikiPage />} />

            {/* 知识库管理 */}
            <Route path="knowledgebase" element={<KnowledgeBaseManagePageWrapper />} />

            {/* 知识库上传 */}
            <Route path="knowledgebase/upload" element={<KnowledgeBaseUploadPageWrapper />} />

            {/* 问答助手（知识库聊天） */}
            <Route path="knowledgebase/chat" element={<KnowledgeBaseQueryPageWrapper />} />

            {/* 设置 */}
            <Route path="settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

function KnowledgeBaseManagePageWrapper() {
  const navigate = useNavigate();

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  const handleChat = () => {
    navigate('/knowledgebase/chat');
  };

  return <KnowledgeBaseManagePage onUpload={handleUpload} onChat={handleChat} />;
}

// 知识库问答页面包装器
function KnowledgeBaseQueryPageWrapper() {
  const navigate = useNavigate();
  const location = useLocation();
  const isChatMode = location.pathname === '/knowledgebase/chat';

  const handleBack = () => {
    if (isChatMode) {
      navigate('/knowledgebase');
    } else {
      navigate('/knowledgebase');
    }
  };

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  return <KnowledgeBaseQueryPage onBack={handleBack} onUpload={handleUpload} />;
}

// 知识库上传页面包装器
function KnowledgeBaseUploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (_result: UploadKnowledgeBaseResponse) => {
    // 上传完成后返回管理页面
    navigate('/knowledgebase');
  };

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  return <KnowledgeBaseUploadPage onUploadComplete={handleUploadComplete} onBack={handleBack} />;
}

export default App;
