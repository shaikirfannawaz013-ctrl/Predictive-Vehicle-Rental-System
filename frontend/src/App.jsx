import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import Register from './pages/Register';
import Search from './pages/Search';
import VehicleDetail from './pages/VehicleDetail';
import Checkout from './pages/Checkout';
import MyBookings from './pages/MyBookings';
import Dashboard from './pages/admin/Dashboard';
import DemandPricing from './pages/admin/DemandPricing';
import Maintenance from './pages/admin/Maintenance';
import CustomerRisk from './pages/admin/CustomerRisk';
import LateReturns from './pages/admin/LateReturns';
import Allocation from './pages/admin/Allocation';
import NotFound from './pages/NotFound';

function Home() {
  const { user, isAdmin } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <Navigate to={isAdmin ? '/admin' : '/search'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/" element={<Home />} />

      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/search" element={<Search />} />
        <Route path="/vehicles/:id" element={<VehicleDetail />} />
        <Route path="/checkout/:bookingId" element={<Checkout />} />
        <Route path="/bookings" element={<MyBookings />} />
      </Route>

      <Route element={<ProtectedRoute role="ADMIN"><Layout /></ProtectedRoute>}>
        <Route path="/admin" element={<Dashboard />} />
        <Route path="/admin/demand" element={<DemandPricing />} />
        <Route path="/admin/allocation" element={<Allocation />} />
        <Route path="/admin/maintenance" element={<Maintenance />} />
        <Route path="/admin/risk" element={<CustomerRisk />} />
        <Route path="/admin/late-returns" element={<LateReturns />} />
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
