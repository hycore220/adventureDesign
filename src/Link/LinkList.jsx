import { useState, useEffect } from "react";
import { useLinkData } from "./LinkDataContext";
import { fetch_Delete, fetch_Put } from "../fetch";

export default function LinkList() {
    const { userName, refresh, onRefresh } = useLinkData();
    const [linkList, setLinkList] = useState([]);
    const [editId, setEditId] = useState(null);      // 수정 중인 id
    const [editData, setEditData] = useState({});    // 수정 중인 데이터

    useEffect(() => {
        if (!userName) return;

        fetch(`http://localhost:8080/link/${userName}`)
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) setLinkList(data);
                else setLinkList([]);
            })
            .catch(() => setLinkList([]));
    }, [userName, refresh]);

    // 수정 버튼 클릭 - 해당 row를 입력 가능하게
    const onEditClick = (item) => {
        setEditId(item.id);
        setEditData({ link: item.link, userName: item.userName, PARAStatus: item.pARASatus });
    };

    // 수정 확인
    const onEditConfirm = async (id) => {
        await fetch_Put(`/link/${id}`, editData);
        setEditId(null);
        onRefresh();
    };

    // 삭제
    const onDelete = async (id) => {
        await fetch_Delete(`/link/${id}`);
        onRefresh();
    };

    return (
        <div style={{
            width: '420px',
            margin: '16px auto 0',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px'
        }}>
            {linkList.length === 0 && userName && (
                <p style={{ color: '#9ca3af', textAlign: 'center', fontSize: '14px' }}>
                    링크가 없어요
                </p>
            )}
            {linkList.map(item => (
                <div key={item.id} style={{
                    backgroundColor: '#1f2937',
                    borderRadius: '10px',
                    padding: '14px 18px',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.3)'
                }}>
                    {editId === item.id ? (
                        // 수정 모드
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            <input
                                value={editData.link}
                                onChange={(e) => setEditData(prev => ({ ...prev, link: e.target.value }))}
                                placeholder="링크"
                                style={inputStyle}
                            />
                            <input
                                value={editData.PARAStatus}
                                onChange={(e) => setEditData(prev => ({ ...prev, PARAStatus: e.target.value }))}
                                placeholder="PARA Status"
                                style={inputStyle}
                            />
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button onClick={() => onEditConfirm(item.id)} style={confirmBtnStyle}>확인</button>
                                <button onClick={() => setEditId(null)} style={cancelBtnStyle}>취소</button>
                            </div>
                        </div>
                    ) : (
                        // 일반 모드
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <div>
                                <p style={{ color: 'white', fontSize: '14px', marginBottom: '4px' }}>
                                    🔗 {item.link}
                                </p>
                                <p style={{ color: '#9ca3af', fontSize: '12px' }}>
                                    {item.PARAStatus}
                                </p>
                            </div>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button onClick={() => onEditClick(item)} style={editBtnStyle}>수정</button>
                                <button onClick={() => onDelete(item.id)} style={deleteBtnStyle}>삭제</button>
                            </div>
                        </div>
                    )}
                </div>
            ))}
        </div>
    );
}

const inputStyle = {
    padding: '6px 10px',
    borderRadius: '6px',
    border: '1px solid #374151',
    backgroundColor: '#111827',
    color: 'white',
    fontSize: '13px',
    outline: 'none',
    width: '100%'
};

const editBtnStyle = {
    padding: '4px 12px',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#4f8ef7',
    color: 'white',
    fontSize: '12px',
    cursor: 'pointer'
};

const deleteBtnStyle = {
    padding: '4px 12px',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#ef4444',
    color: 'white',
    fontSize: '12px',
    cursor: 'pointer'
};

const confirmBtnStyle = {
    padding: '4px 12px',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#22c55e',
    color: 'white',
    fontSize: '12px',
    cursor: 'pointer',
    flex: 1
};

const cancelBtnStyle = {
    padding: '4px 12px',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#6b7280',
    color: 'white',
    fontSize: '12px',
    cursor: 'pointer',
    flex: 1
};