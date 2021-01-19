import { render } from './index';

export interface IPlayer {
    name: string;
    points: number;
    online: boolean;
    answers: boolean;
    readyToAnswer: boolean;
}

export function playerIsLocal(player: IPlayer) {
    return player.name == state.playerName;
}

export interface ICategory {
    name: string,
    questions: Array<number>
}

export class GlobalState {
    connecting: boolean = true;
    authorized: boolean = false;
    permissions: "ADMIN" | "PLAYER" | "SPECTATOR";
    gamePhase: "SPLASHSCREEN" | "QUESTIONSTABLE" | "QUESTION" | "ENDGAME";
    playerName: string = "";
    categories = new Array<ICategory>();
    players = new Array<IPlayer>();
    playerAvatars = new Map<string, string>();
    questionText: string;
    questionImage: string;
    questionAnswer: string;
    timerStart: number = 0;
    timerEnd: number = 0;
}

export const state = new GlobalState();

declare global {
    interface Window {
        debug: any
    }
}

window.debug = state;

document.body.onkeydown = (e) => {
    if(e.code == "Space" && !e.repeat) {
        sendToServer({
            type: 'spacePressed'
        });
    }
}

export function sendToServer(message: any) {
    socket.send(JSON.stringify(message));
}

export function tryAuth() {
    let playerName = localStorage.getItem("playerName");
    let key = localStorage.getItem("key");
    if(playerName == null || key == null) {
        state.connecting = false;
        state.authorized = false;
    } else {
        sendToServer({
            type: "auth",
            name: playerName,
            key: key
        });
    }
    render();
}

let socket = new WebSocket(`ws://${location.host}/`);

socket.onopen = (e) => {
    tryAuth();
}

socket.onmessage = (e) => {
    let message = JSON.parse(e.data);
    if(message.type == "auth") {
        state.connecting = false;
        state.authorized = message.authorized;
        state.permissions = message.permissions;
        state.playerName = message.playerName;
        render();
    }
    if(message.type == "updateState") {
        delete message.type;
        Object.assign(state, message);
        render();
    }
    if(message.type == "updatePlayerAvatar") {
        state.playerAvatars.set(message.name, message.avatar);
        render();
    }
    if(message.type == "setTimer") {
        state.timerStart = performance.now();
        state.timerEnd = performance.now() + message.milliseconds;
        render();
    }
}