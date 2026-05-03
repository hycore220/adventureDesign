import { useCallback, useState } from "react";
import { fetch_Post } from "../fetch";
import { useLinkData } from "./LinkDataContext";
import "./Create.css";

export default function Create() {
    const { userName, setUserName, onRefresh } = useLinkData();

    const [linkData, setLinkData] = useState({
        link: '',
        PARAStatus: '',
    });

    const changeLinkData = useCallback((e) => {
        const { name, value } = e.target;
        setLinkData(prev => ({ ...prev, [name]: value }));
    }, []);

    const onClick = useCallback(async () => {
        await fetch_Post("/link/create", { ...linkData, userName });
        onRefresh();
    }, [linkData, userName, onRefresh]);

    return (
        <div className="container">
            <div className="card">
                <h2 className="title">링크 추가</h2>

                <div className="input-row">
                    <label className="input-label">User Name</label>
                    <input
                        name="userName"
                        value={userName}
                        onChange={(e) => setUserName(e.target.value)}
                        className="input-field"
                        placeholder="이름을 입력하세요"
                    />
                </div>

                {[
                    { label: 'Link', name: 'link', placeholder: 'https://...' },
                    { label: 'PARA Status', name: 'PARAStatus', placeholder: 'P / A / R / A' },
                ].map(({ label, name, placeholder }) => (
                    <div key={name} className="input-row">
                        <label className="input-label">{label}</label>
                        <input
                            name={name}
                            value={linkData[name]}
                            onChange={changeLinkData}
                            className="input-field"
                            placeholder={placeholder}
                        />
                    </div>
                ))}

                <button className="submit-btn" onClick={onClick}>추가</button>
            </div>
        </div>
    );
}