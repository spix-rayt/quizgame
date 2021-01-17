import { stat } from 'fs/promises';
import { render } from './index';

export interface IPlayer {
    avatar: string,
    name: string,
    points: number,
    online: boolean
}

export interface ICategory {
    name: string,
    questions: Array<number>
}

export const state = {
    playerName: localStorage.getItem("playerName") ?? "",
    connecting: true,
    authorized: false,
    permissions: "",
    gamePhase: "",
    localPlayerShouldAnswer: false,
    categories: new Array<ICategory>(),
    players: new Array<IPlayer>(),
    questionText: ""
}

declare global {
    interface Window {
        debug: any
    }
}

window.debug = state;

export function sendToServer(message: any) {
    socket.send(JSON.stringify(message));
}

export function tryAuth() {
    state.playerName = localStorage.getItem("playerName");
    let key = localStorage.getItem("key");
    if(state.playerName == null || key == null) {
        state.connecting = false;
        state.authorized = false;
        render();
    } else {
        sendToServer({
            type: "auth",
            name: state.playerName,
            key: key
        });
    }
}

let socket = new WebSocket("ws://localhost:8080/");

socket.onopen = (e) => {
    console.log("connected");
    tryAuth();
}

socket.onmessage = (e) => {
    let message = JSON.parse(e.data);
    if(message.type == "auth") {
        state.connecting = false;
        state.authorized = message.result;
        state.permissions = message.permissions;
        render();
    }
    if(message.type == "updateState") {
        delete message.type;
        Object.assign(state, message);
        render();
    }
}