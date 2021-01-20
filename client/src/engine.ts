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
    gamePhase: "SPLASHSCREEN" | "QUESTIONSTABLE" | "QUESTION" | "ANSWER" | "ENDGAME";
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

let testAudio = new Audio("quack_5.mp3");

document.body.onkeydown = (e) => {
    console.log(e);
    if(e.code == "Space" && !e.repeat) {
        sendToServer({
            type: 'spacePressed'
        });
    }
    if(e.code == "Enter" && !e.repeat) {
        testAudio.play();
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

export function uploadAvatar() {
    let input = document.createElement('input') as HTMLInputElement;
    input.type = "file";
    input.click();
    input.onchange = function(e) {
        if(input.files[0].size > 1024 * 1024 * 5) {
            alert("Слишком большой файл");
        }
        let fileReader = new FileReader();
        fileReader.onloadend = (e) => {
            let base64Image = e.target.result as string;
            sendToServer({
                type: 'uploadAvatar',
                image: base64Image
            });
        }
        fileReader.readAsDataURL(input.files[0]);
    }
}