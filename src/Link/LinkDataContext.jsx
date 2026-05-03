import { createContext, useContext, useState } from "react";

const LinkDataContext = createContext(null);

export function LinkDataProvider({ children }) {
    const [userName, setUserName] = useState('');
    const [refresh, setRefresh] = useState(0);

    const onRefresh = () => setRefresh(prev => prev + 1);

    return (
        <LinkDataContext.Provider value={{ userName, setUserName, refresh, onRefresh }}>
            {children}
        </LinkDataContext.Provider>
    );
}

export function useLinkData() {
    return useContext(LinkDataContext);
}