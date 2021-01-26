import { stat } from 'fs/promises';
import { render } from './index';
import MeathookAudioPath from './meathook.mp3';

export const globalVolume = 0.4;

export let meathookAudio = new Audio(MeathookAudioPath);
meathookAudio.volume = globalVolume;

export interface IPlayer {
    name: string;
    points: number;
    online: boolean;
    answers: boolean;
    readyToAnswer: boolean;
    selectsNextQuestion: boolean;
    shouldSelectedByAdmin: boolean;
}

export function playerIsLocal(player: IPlayer) {
    return player.name == state.playerName;
}

export interface ICategory {
    name: string,
    questions: Array<number>
}

export interface IAnswer {
    text: string,
    video: string | null
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
    questionText: string = "";
    questionImage: string | null;
    questionAudio: string | null;
    questionVideo: string | null;
    questionAnswer: string = "";
    timerStart: number = 0;
    timerEnd: number = 0;
    questionAudioPlaying: boolean = false;
    questionCatTrap: boolean = false;
    fullscreenAnimation = false;
    answer: IAnswer | null = null;
    playMedia: () => void = () => {};
}

export const state = new GlobalState();

declare global {
    interface Window {
        debug: any
    }
}

window.debug = state;

document.body.onkeydown = (e) => {
    console.log(e);
    if(e.code == "Space" && !e.repeat) {
        sendToServer({
            type: 'spacePressed'
        });
    }
    if(e.code == "KeyT" && e.shiftKey && !e.repeat) {
        sendToServer({
            type: 'testQuestion'
        });
    }
    if(e.code == "KeyN" && e.shiftKey && !e.repeat) {
        sendToServer({
            type: 'debug.nextRound'
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
    console.log(message);
    if(message.type == "auth") {
        state.connecting = false;
        state.authorized = message.authorized;
        state.permissions = message.permissions;
        state.playerName = message.playerName;
        render();
    }
    if(message.type == "updateState") {
        delete message.type;

        if(state.questionCatTrap == false && message.questionCatTrap == true) {
            meathookAudio.play();
            state.fullscreenAnimation = true;
        }

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
    if(message.type == "playMedia") {
        state.playMedia();
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