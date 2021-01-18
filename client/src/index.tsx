import './styles.scss';
import React, { ChangeEvent, SyntheticEvent, useState } from 'react';
import ReactDOM from 'react-dom';
import { ICategory, IPlayer, playerIsLocal, sendToServer, state, tryAuth } from './logic';
import SpeechBubbleSvg from './three-dots-in-speech-bubble.svg';
import CheckMarkSvg from './check-mark.svg';

let questionsTableWidth = 40;

function getTextWidth(text: string, font: string) {
    let canvas = document.createElement("canvas")
    var context = canvas.getContext("2d");
    context.font = font;
    var metrics = context.measureText(text);
    return metrics.width;
}

function App() {
    if(state.connecting) {
        return <div></div>
    } else {
        if(state.authorized) {
            return <Game></Game>
        } else {
            return <Auth></Auth>
        }
    }
}

function Auth() {
    const [playerName, setPlayerName] = useState(localStorage.getItem("playerName"));
    const [key, setKey] = useState("");
    
    let handleNameChange = (e: ChangeEvent<HTMLInputElement>) => setPlayerName(e.target.value);

    let handleKeyChange = (e: ChangeEvent<HTMLInputElement>) => setKey(e.target.value);
    
    let handleButtonClick = (e: SyntheticEvent) => {
        localStorage.setItem("playerName", playerName);
        localStorage.setItem("key", key);
        tryAuth();
    }

    return <div className="auth">
        <div>
            <span>Имя</span><input type="text" onChange={handleNameChange} value={playerName}></input>
        </div>
        <div>
            <span>Код</span><input type="password" onChange={handleKeyChange}></input>
        </div>
        <button onClick={handleButtonClick}>Вход</button>
    </div>
}

function Game() {
    let gamePhaseComponent;
    if(state.gamePhase == "SPLASHSCREEN") {
        gamePhaseComponent = <SplashScreen></SplashScreen>
    }
    if(state.gamePhase == "QUESTIONSTABLE") {
        gamePhaseComponent = <QuestionsTable></QuestionsTable>
    }
    if(state.gamePhase == "QUESTION") {
        gamePhaseComponent = <QuestionBlock></QuestionBlock>
    }
    if(state.gamePhase == "ENDGAME") {
        gamePhaseComponent = <div></div>
    }

    let localPlayerShouldAnswer = false;
    for(let player of state.players) {
        if(playerIsLocal(player) && player.answers) {
            localPlayerShouldAnswer = true;
            break;
        }
    }

    return <div className="game">
        {gamePhaseComponent}
        <div className="players-container">
            {
                state.players.map((e) => {
                    return <Player player={e} key={e.name}></Player>
                })
            }
        </div>
        { localPlayerShouldAnswer ? <div className="screenLightLeft"></div> : null}
        { localPlayerShouldAnswer ? <div className="screenLightRight"></div> : null}
        { state.permissions == 'ADMIN' ? <div className="answerField">{state.questionAnswer}</div> : null}
    </div>
}

function SplashScreen() {
    const handleStartGameButton = (e: SyntheticEvent) => {
        sendToServer({type: "startGame"});
    }

    return <div className="splashScreen">
        <div>Ожидание начала игры</div>
        <div>
            { state.permissions == "ADMIN" ? <button onClick={handleStartGameButton}>Начать</button> : null }
        </div>
    </div>
}

function QuestionsTable() {
    let questionsInCategory = 0;
    state.categories.forEach((e) => {
        if(e.questions.length > questionsInCategory) {
            questionsInCategory = e.questions.length;
        }
    });

    return <div className="questionsTable" style={{width: `${questionsTableWidth}vw`}}>
        {
            state.categories.map((category, ci) => {
                return <CategoryRow category={category} categoryIndex={ci} questionsInCategory={questionsInCategory} key={ci}></CategoryRow>
            })
        }
    </div>
}

function CategoryRow(prop: {category: ICategory, categoryIndex: number, questionsInCategory:number}) {
    let result = [];
    let categoryStyle = {
        width: (questionsTableWidth * 0.35) + "vw",
        height: (questionsTableWidth * 0.65 / prop.questionsInCategory) + "vw"
    }
    let priceButtonStyle = {
        width: (questionsTableWidth * 0.65 / prop.questionsInCategory) + "vw",
        height: (questionsTableWidth * 0.65 / prop.questionsInCategory) + "vw"
    }
    result.push(<div className="category" style={categoryStyle} key="category">{prop.category.name}</div>);
    for(let i = 0; i < prop.questionsInCategory; i++) {
        let price;
        if(i < prop.category.questions.length) {
            price = prop.category.questions[i];
        } else {
            price = "";
        }
        let handleButtonClick = (e: SyntheticEvent) => {
            sendToServer({
                type: "questionOpen",
                category: prop.categoryIndex,
                question: i
            });
        }
        result.push(<div className="priceButton" style={priceButtonStyle}  key={i} onClick={handleButtonClick}>{price}</div>);
    }

    return <div className="categoryRow">{result}</div>
}



function QuestionBlock() {
    let textWidth = getTextWidth(state.questionText, "1.0vw Futura Condensed");
    let questionsTableWidthInPx = window.innerWidth * questionsTableWidth / 100;
    let fontSize = 4.5 / (textWidth / questionsTableWidthInPx);
    if(fontSize > 3.0) {
        fontSize = 3.0;
    }
    if(fontSize < 0.8) {
        fontSize = 0.8;
    }

    return <div className="questionBlock" style={{width: questionsTableWidth + "vw", height: (questionsTableWidth * 0.65) + "vw", fontSize: `${fontSize}vw`}}>
        {state.questionText}
        { state.questionImage != null ? <img src={`/file/${state.questionImage}`}></img> : null}
    </div>
}

function Player(prop: {player: IPlayer}) {
    let uploadAvatar = () => {};

    if(playerIsLocal(prop.player)) {
        uploadAvatar = () => {
            let input = document.createElement('input') as HTMLInputElement;
            input.type = "file";
            input.click();
            input.onchange = function(e) {
                let fileReader = new FileReader();
                fileReader.onload = function(e) {
                    let bytes = e.target.result;
                    if(bytes instanceof ArrayBuffer) {
                        if(bytes.byteLength < 1024 * 1024 * 5) {
                            var base64Image = "data:application/octet-stream;base64," + btoa(String.fromCharCode.apply(null, new Uint8Array(bytes)));
                            sendToServer({
                                type: 'uploadAvatar',
                                image: base64Image
                            });
                        } else {
                            alert("Максимальный размер файла 5MiB");
                        }
                    }
                }
    
                fileReader.readAsArrayBuffer(input.files[0]);
            }
        }
    }
    

    let avatarBase64 = state.playerAvatars.get(prop.player.name) ?? "";

    return <div className={`player ${prop.player.answers ? 'answers' : ''} ${playerIsLocal(prop.player) ? 'local' : ''}`}>
        <div className="icons">
            {prop.player.readyToAnswer ? <img src={CheckMarkSvg}></img> : null}
        </div>
        <img src={avatarBase64} className="avatar" onClick={uploadAvatar}></img>
        <div className="points">{prop.player.points}</div>
        <div className="name">{prop.player.name}</div>
    </div>
}

export let render = () => ReactDOM.render(<App />, document.querySelector('#root'));
render();