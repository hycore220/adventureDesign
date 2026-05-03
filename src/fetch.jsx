const rootUrl = 'http://localhost:8080'

export const fetch_Post = async (address, data) => {
    address = rootUrl + address;
    try {
        const response = await fetch(address, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        const result = await response.text();
        console.log(result);
        return result;
    } catch (error) {
        console.error(error);
    }
}

export const fetch_Put = async (address, data) => {
    address = rootUrl + address;
    try {
        const response = await fetch(address, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        const result = await response.text();
        console.log(result);
        return result;
    } catch (error) {
        console.error(error);
    }
}

export const fetch_Delete = async (address) => {
    address = rootUrl + address;
    try {
        const response = await fetch(address, {
            method: 'DELETE',
        });
        const result = await response.text();
        console.log(result);
        return result;
    } catch (error) {
        console.error(error);
    }
}