import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { RefreshCcw, Wallet, TrendingUp, XCircle } from 'lucide-react';

// Configure Base URL
const api = axios.create({
    baseURL: 'http://localhost:8080/api/dhan'
});

function App() {
    const [activeTab, setActiveTab] = useState('dashboard');
    const [account, setAccount] = useState(null);
    const [positions, setPositions] = useState([]);
    const [loading, setLoading] = useState(false);

    // Link Account State
    const [creds, setCreds] = useState({ clientId: '', accessToken: '' });

    // Order State
    const [order, setOrder] = useState({
        symbol: '', exchange: 'NSE', transactionType: 'BUY',
        quantity: 1, price: 0, orderType: 'MARKET', productType: 'INTRADAY'
    });

    // 1. Check for Active Account on Load
    useEffect(() => {
        fetchAccount();
    }, []);

    // 2. Poll Positions every 5 seconds if logged in
    useEffect(() => {
        if (account) {
            fetchPositions();
            const interval = setInterval(fetchPositions, 5000); // Real-time feel
            return () => clearInterval(interval);
        }
    }, [account]);

    const fetchAccount = async () => {
        try {
            const res = await api.get('/account');
            if (res.data.success) setAccount(res.data.data);
        } catch (err) {
            console.log("No active account linked");
        }
    };

    const linkAccount = async (e) => {
        e.preventDefault();
        try {
            const res = await api.post('/link-account', creds);
            if (res.data.success) {
                setAccount(res.data.data);
                alert('Account Linked Successfully!');
                fetchPositions();
            }
        } catch (err) {
            alert('Failed to link account');
        }
    };

    const fetchPositions = async () => {
        try {
            const res = await api.get('/positions');
            if (res.data.success) setPositions(res.data.data);
        } catch (err) {
            console.error("Failed to fetch positions");
        }
    };

    const placeOrder = async (e) => {
        e.preventDefault();
        setLoading(true);
        try {
            const res = await api.post('/orders', order);
            if (res.data.success) {
                alert(`Order ${res.data.data.status}`);
                fetchPositions(); // Refresh immediately
            }
        } catch (err) {
            alert('Order Failed: ' + err.message);
        } finally {
            setLoading(false);
        }
    };

    const closePosition = async (orderId) => {
        if(!window.confirm("Are you sure you want to close this?")) return;
        // Note: In a real app, you'd calculate the reverse order.
        // Here we use the backend endpoint you provided which takes an OrderID.
        try {
            await api.delete(`/orders/${orderId}`);
            alert("Close request sent");
            fetchPositions();
        } catch (e) {
            alert("Failed to close");
        }
    };

    return (
        <div className="container">
            {/* Header */}
            <div className="header">
                <h1 style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <TrendingUp color="#3b82f6" /> Dhan Algo Trader
                </h1>
                <div>
                    {account ? (
                        <span className="badge" style={{background: '#064e3b', color: '#34d399'}}>
                ● Connected: {account.clientId}
             </span>
                    ) : (
                        <span className="badge" style={{background: '#7f1d1d', color: '#fca5a5'}}>
                ● Disconnected
             </span>
                    )}
                </div>
            </div>

            {!account ? (
                <div className="card" style={{ maxWidth: '400px', margin: '50px auto' }}>
                    <h2>Link Dhan Account</h2>
                    <form onSubmit={linkAccount}>
                        <label>Client ID</label>
                        <input
                            type="text"
                            placeholder="Enter Client ID"
                            value={creds.clientId}
                            onChange={e => setCreds({...creds, clientId: e.target.value})}
                        />
                        <label>Access Token</label>
                        <input
                            type="password"
                            placeholder="Enter Token"
                            value={creds.accessToken}
                            onChange={e => setCreds({...creds, accessToken: e.target.value})}
                        />
                        <button className="btn btn-primary" style={{width: '100%'}}>Link Account</button>
                    </form>
                </div>
            ) : (
                <div className="grid">
                    {/* LEFT COLUMN: Order Entry */}
                    <div className="card">
                        <h2>Place Order</h2>
                        <form onSubmit={placeOrder}>
                            <label>Symbol</label>
                            <input
                                value={order.symbol}
                                onChange={e => setOrder({...order, symbol: e.target.value.toUpperCase()})}
                                placeholder="e.g. TCS"
                                required
                            />

                            <div style={{display: 'flex', gap: '10px'}}>
                                <div style={{flex:1}}>
                                    <label>Type</label>
                                    <select onChange={e => setOrder({...order, transactionType: e.target.value})}>
                                        <option value="BUY">BUY</option>
                                        <option value="SELL">SELL</option>
                                    </select>
                                </div>
                                <div style={{flex:1}}>
                                    <label>Product</label>
                                    <select onChange={e => setOrder({...order, productType: e.target.value})}>
                                        <option value="INTRADAY">INTRADAY</option>
                                        <option value="DELIVERY">DELIVERY</option>
                                    </select>
                                </div>
                            </div>

                            <div style={{display: 'flex', gap: '10px'}}>
                                <div style={{flex:1}}>
                                    <label>Qty</label>
                                    <input type="number" value={order.quantity} onChange={e => setOrder({...order, quantity: parseInt(e.target.value)})} />
                                </div>
                                <div style={{flex:1}}>
                                    <label>Price (0 for Mkt)</label>
                                    <input type="number" value={order.price} onChange={e => setOrder({...order, price: parseFloat(e.target.value)})} />
                                </div>
                            </div>

                            <button
                                disabled={loading}
                                className={order.transactionType === 'BUY' ? "btn btn-buy" : "btn btn-sell"}
                            >
                                {loading ? 'Processing...' : `${order.transactionType} ${order.symbol}`}
                            </button>
                        </form>
                    </div>

                    {/* RIGHT COLUMN: Positions */}
                    <div className="card">
                        <div className="header">
                            <h2>Open Positions</h2>
                            <button className="btn" onClick={fetchPositions}><RefreshCcw size={16}/></button>
                        </div>

                        <table>
                            <thead>
                            <tr>
                                <th>Symbol</th>
                                <th>Qty</th>
                                <th>Avg Price</th>
                                <th>LTP</th>
                                <th>P&L</th>
                                <th>Action</th>
                            </tr>
                            </thead>
                            <tbody>
                            {positions.length === 0 ? (
                                <tr><td colspan="6" style={{textAlign:'center', padding: '20px'}}>No open positions</td></tr>
                            ) : (
                                positions.map((pos, index) => (
                                    <tr key={index}>
                                        <td>
                                            <div style={{fontWeight: 'bold'}}>{pos.symbol}</div>
                                            <small style={{color: '#64748b'}}>{pos.exchange} | {pos.productType}</small>
                                        </td>
                                        <td style={{color: pos.positionType === 'LONG' ? 'var(--success)' : 'var(--danger)'}}>
                                            {pos.quantity}
                                        </td>
                                        <td>{pos.avgPrice.toFixed(2)}</td>
                                        <td>{pos.ltp.toFixed(2)}</td>
                                        <td className={pos.pnl >= 0 ? 'pnl-pos' : 'pnl-neg'} style={{fontWeight:'bold'}}>
                                            {pos.pnl >= 0 ? '+' : ''}{pos.pnl.toFixed(2)}
                                        </td>
                                        <td>
                                            <button className="btn-danger" style={{padding: '5px 10px'}} onClick={() => closePosition('mock-order-id')}>
                                                Exit
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}

export default App;