import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { RefreshCcw, Wallet, TrendingUp, XCircle, Search, X } from 'lucide-react';

const api = axios.create({
    baseURL: 'http://localhost:8080/api/dhan'
});

function App() {
    const [activeTab, setActiveTab] = useState('dashboard');
    const [account, setAccount] = useState(null);
    const [positions, setPositions] = useState([]);
    const [loading, setLoading] = useState(false);

    const [creds, setCreds] = useState({ clientId: '', accessToken: '' });

    const [order, setOrder] = useState({
        symbol: '',
        securityId: '',
        exchange: 'NSE_EQ',
        transactionType: 'BUY',
        quantity: 1,
        price: 0,
        orderType: 'MARKET',
        productType: 'INTRADAY'
    });

    // Symbol Search State
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [showSearchResults, setShowSearchResults] = useState(false);
    const [searchLoading, setSearchLoading] = useState(false);

    useEffect(() => {
        fetchAccount();
    }, []);

    useEffect(() => {
        if (account) {
            fetchPositions();
            const interval = setInterval(fetchPositions, 5000);
            return () => clearInterval(interval);
        }
    }, [account]);

    // Debounced Symbol Search
    useEffect(() => {
        if (searchQuery.length >= 2) {
            const timer = setTimeout(() => {
                searchSymbols(searchQuery);
            }, 300);
            return () => clearTimeout(timer);
        } else {
            setSearchResults([]);
            setShowSearchResults(false);
        }
    }, [searchQuery]);

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
            alert('Failed to link account: ' + (err.response?.data?.message || err.message));
        }
    };

    const fetchPositions = async () => {
        try {
            const res = await api.get('/positions');
            if (res.data.success) {
                console.log('Positions received:', res.data.data);
                setPositions(res.data.data);
            }
        } catch (err) {
            console.error("Failed to fetch positions");
        }
    };

    const searchSymbols = async (query) => {
        setSearchLoading(true);
        try {
            const res = await api.post('/symbols/search', {
                query: query,
                exchange: order.exchange.split('_')[0],
                limit: 10
            });
            if (res.data.success) {
                console.log('Search results:', res.data.data);
                setSearchResults(res.data.data);
                setShowSearchResults(true);
            }
        } catch (err) {
            console.error("Failed to search symbols", err);
            setSearchResults([]);
        } finally {
            setSearchLoading(false);
        }
    };

    const selectSymbol = (symbol) => {
        console.log('Selected symbol:', symbol);
        setOrder({
            ...order,
            symbol: symbol.tradingSymbol,
            securityId: symbol.securityId
        });
        setSearchQuery(symbol.tradingSymbol);
        setShowSearchResults(false);
    };

    const placeOrder = async (e) => {
        e.preventDefault();

        if (!order.securityId) {
            alert('Please select a valid symbol from the search results');
            return;
        }

        setLoading(true);
        try {
            const orderPayload = {
                ...order,
                symbol: order.securityId
            };

            console.log('Placing order:', orderPayload);
            const res = await api.post('/orders', orderPayload);
            if (res.data.success) {
                alert(`Order ${res.data.data.status}`);
                fetchPositions();
                setSearchQuery('');
                setOrder({
                    ...order,
                    symbol: '',
                    securityId: '',
                    quantity: 1,
                    price: 0
                });
            }
        } catch (err) {
            alert('Order Failed: ' + (err.response?.data?.message || err.message));
        } finally {
            setLoading(false);
        }
    };

    const closePosition = async (position) => {
        console.log('Closing position:', position);

        if (!window.confirm(`Are you sure you want to close ${position.quantity} qty of ${position.symbol}?`)) {
            return;
        }

        if (!position.securityId) {
            alert('Security ID not available for this position');
            return;
        }

        setLoading(true);
        try {
            const closeRequest = {
                symbol: position.symbol,
                securityId: position.securityId,  // Use the stored securityId
                exchange: position.exchange,
                quantity: position.quantity,
                productType: position.productType,
                positionType: position.positionType
            };

            console.log('Close request:', closeRequest);
            const res = await api.post('/positions/close', closeRequest);

            if (res.data.success) {
                alert("Position closed successfully");
                fetchPositions();
            }
        } catch (err) {
            const errorMsg = err.response?.data?.message || err.message;
            alert("Failed to close position: " + errorMsg);
            console.error('Close position error:', err.response?.data);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="container">
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
                    <div className="card">
                        <h2>Place Order</h2>
                        <form onSubmit={placeOrder}>
                            <label>Search Symbol</label>
                            <div style={{ position: 'relative', marginBottom: '15px' }}>
                                <div style={{ position: 'relative' }}>
                                    <Search style={{
                                        position: 'absolute',
                                        left: '10px',
                                        top: '50%',
                                        transform: 'translateY(-50%)',
                                        color: '#64748b',
                                        pointerEvents: 'none'
                                    }} size={18} />
                                    <input
                                        type="text"
                                        placeholder="Type to search (e.g., RELIANCE, TCS)"
                                        value={searchQuery}
                                        onChange={e => setSearchQuery(e.target.value.toUpperCase())}
                                        style={{
                                            width: '100%',
                                            padding: '10px 40px 10px 40px',
                                            borderRadius: '6px',
                                            border: '1px solid #334155',
                                            background: '#0f172a',
                                            color: 'white',
                                            boxSizing: 'border-box'
                                        }}
                                        autoComplete="off"
                                    />
                                    {searchQuery && (
                                        <X
                                            style={{
                                                position: 'absolute',
                                                right: '10px',
                                                top: '50%',
                                                transform: 'translateY(-50%)',
                                                cursor: 'pointer',
                                                color: '#64748b'
                                            }}
                                            size={18}
                                            onClick={() => {
                                                setSearchQuery('');
                                                setOrder({ ...order, symbol: '', securityId: '' });
                                            }}
                                        />
                                    )}
                                </div>

                                {showSearchResults && (
                                    <div style={{
                                        position: 'absolute',
                                        top: '100%',
                                        left: 0,
                                        right: 0,
                                        background: '#1e293b',
                                        border: '1px solid #334155',
                                        borderRadius: '6px',
                                        marginTop: '5px',
                                        maxHeight: '200px',
                                        overflowY: 'auto',
                                        zIndex: 1000,
                                        boxShadow: '0 4px 6px -1px rgba(0,0,0,0.3)'
                                    }}>
                                        {searchLoading ? (
                                            <div style={{ padding: '10px', textAlign: 'center', color: '#64748b' }}>
                                                Searching...
                                            </div>
                                        ) : searchResults.length === 0 ? (
                                            <div style={{ padding: '10px', textAlign: 'center', color: '#64748b' }}>
                                                No results found
                                            </div>
                                        ) : (
                                            searchResults.map((symbol, idx) => (
                                                <div
                                                    key={idx}
                                                    onClick={() => selectSymbol(symbol)}
                                                    style={{
                                                        padding: '10px',
                                                        cursor: 'pointer',
                                                        borderBottom: idx < searchResults.length - 1 ? '1px solid #334155' : 'none',
                                                        transition: 'background 0.2s'
                                                    }}
                                                    onMouseEnter={e => e.target.style.background = '#334155'}
                                                    onMouseLeave={e => e.target.style.background = 'transparent'}
                                                >
                                                    <div style={{ fontWeight: 'bold' }}>
                                                        {symbol.tradingSymbol}
                                                    </div>
                                                    <div style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
                                                        {symbol.name} • {symbol.exchangeSegment}
                                                    </div>
                                                </div>
                                            ))
                                        )}
                                    </div>
                                )}
                            </div>

                            {order.securityId && (
                                <div style={{
                                    padding: '8px',
                                    background: '#064e3b',
                                    color: '#34d399',
                                    borderRadius: '6px',
                                    marginBottom: '15px',
                                    fontSize: '0.85rem'
                                }}>
                                    ✓ Selected: {order.symbol}
                                </div>
                            )}

                            <div style={{display: 'flex', gap: '10px', marginBottom: '15px'}}>
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
                                        <option value="CNC">DELIVERY</option>
                                    </select>
                                </div>
                            </div>

                            <div style={{display: 'flex', gap: '10px', marginBottom: '15px'}}>
                                <div style={{flex:1}}>
                                    <label>Qty</label>
                                    <input type="number" value={order.quantity} onChange={e => setOrder({...order, quantity: parseInt(e.target.value)})} />
                                </div>
                                <div style={{flex:1}}>
                                    <label>Order Type</label>
                                    <select onChange={e => setOrder({...order, orderType: e.target.value})}>
                                        <option value="MARKET">MARKET</option>
                                        <option value="LIMIT">LIMIT</option>
                                    </select>
                                </div>
                            </div>

                            {order.orderType === 'LIMIT' && (
                                <div style={{marginBottom: '15px'}}>
                                    <label>Price</label>
                                    <input
                                        type="number"
                                        step="0.05"
                                        value={order.price}
                                        onChange={e => setOrder({...order, price: parseFloat(e.target.value)})}
                                    />
                                </div>
                            )}

                            <button
                                disabled={loading}
                                className={order.transactionType === 'BUY' ? "btn btn-buy" : "btn btn-sell"}
                            >
                                {loading ? 'Processing...' : `${order.transactionType} ${order.symbol || 'Select Symbol'}`}
                            </button>
                        </form>
                    </div>

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
                                <tr><td colSpan="6" style={{textAlign:'center', padding: '20px'}}>No open positions</td></tr>
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
                                            <button
                                                className="btn-danger"
                                                style={{padding: '5px 10px'}}
                                                onClick={() => closePosition(pos)}
                                                disabled={loading}
                                            >
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