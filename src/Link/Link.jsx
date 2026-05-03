import { LinkDataProvider } from "./LinkDataContext";
import Create from "./Create";
import LinkList from "./LinkList";

export default function Link() {
    return (
        <LinkDataProvider>
            <Create />
            <LinkList />
        </LinkDataProvider>
    );
}