import './styles.scss';
import React, { ChangeEvent, SyntheticEvent, useEffect, useRef, useState } from 'react';
import ReactDOM from 'react-dom';
import { ICategory, IPlayer, playerIsLocal, sendToServer, state, tryAuth, uploadAvatar, meathookAudio } from './engine';
import AudioSvg from './audio.svg';
import VideoSvg from './video-camera.svg';
import CheckMarkSvg from './check-mark.svg';
import MeatHookImgPath from './meathook.jpg';

let preloadedMeatHookImg = new Image();
preloadedMeatHookImg.src = MeatHookImgPath;

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
    if(state.fullscreenAnimation) {
        if(state.questionCatTrap) {
            return <CatTrapBlock></CatTrapBlock>
        }
    }

    if(state.answer == null) {
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
    } else {
        gamePhaseComponent = <AnswerBlock></AnswerBlock>
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
        <Timer></Timer>
        <AdminAnswerConfirmation></AdminAnswerConfirmation>
        <div className="players-container">
            {
                state.players.map((e) => {
                    return <Player player={e} key={e.name}></Player>
                })
            }
        </div>
        { localPlayerShouldAnswer ? <div className="screenLightLeft"></div> : null}
        { localPlayerShouldAnswer ? <div className="screenLightRight"></div> : null}
        { state.permissions == 'ADMIN' ? <div className="adminField"><span>{`${state.questionText}\n`}</span><span>{state.questionAnswer}</span></div> : null}
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
                type: "questionSelect",
                category: prop.categoryIndex,
                question: i
            });
        }
        result.push(<div className="priceButton" style={priceButtonStyle}  key={i} onClick={handleButtonClick}>{price}</div>);
    }

    return <div className="categoryRow">{result}</div>
}

function QuestionBlock() {
    const videoRef = useRef<HTMLVideoElement>(null);
    const audioRef = useRef<HTMLAudioElement>(null);
    const [audioPlaying, setAudioPlaying] = useState(false);
    const [showVideo, setShowVideo] = useState(false);
    
    let textWidth = getTextWidth(state.questionText, "1.0vw Futura Condensed");
    let questionsTableWidthInPx = window.innerWidth * questionsTableWidth / 100;
    let fontSize = 4.5 / (textWidth / questionsTableWidthInPx);
    if(fontSize > 3.0) {
        fontSize = 3.0;
    }
    if(fontSize < 0.8) {
        fontSize = 0.8;
    }

    let element: any = <div></div>
    if(state.questionImage != null) {
        element = <img className="questionImage" src={`/file/${state.questionImage}`}></img>
    } else if(state.questionAudio != null) {
        element = [<img className={`audioSvg ${audioPlaying ? 'playing' : ''}`} src={AudioSvg}></img>, <audio src={`/file/${state.questionAudio}`} ref={audioRef}></audio>]
    } else if(state.questionVideo != null) {
        element = [];
        if(!showVideo) {
            element.push(<img className="videoSvg" src={VideoSvg}></img>);
        }
        element.push(<video className={`${showVideo ? 'videoVisible' : 'videoInvisible'}`} src={`/file/${state.questionVideo}`} ref={videoRef} preload="auto"></video>);
    } else {
        element = state.questionText;
    }

    useEffect(() => {
        state.playMedia = () => {};
        if(state.questionAudio != null) {
            let audioElement = audioRef.current;
            audioElement.oncanplaythrough = () => {
                state.playMedia = () => {
                    setTimeout(() => {
                        audioRef.current.volume = 0.4;
                        audioRef.current.play();
                        setAudioPlaying(true);
                        audioRef.current.onended = () => {
                            setAudioPlaying(false);
                        }
                    }, 1000);
                }
                sendToServer({
                    type: 'mediaReady'
                });
            }
        } else if(state.questionVideo != null) {
            let videoElement = videoRef.current;
            videoElement.oncanplaythrough = () => {
                state.playMedia = () => {
                    setTimeout(() => {
                        setShowVideo(true);
                        videoRef.current.volume = 0.4;
                        videoRef.current.play();
                    }, 1000);
                }
                sendToServer({
                    type: 'mediaReady'
                });
            }
        }
    }, []);

    return <div className="questionBlock" style={{width: questionsTableWidth + "vw", height: (questionsTableWidth * 0.65) + "vw", fontSize: `${fontSize}vw`}}>
        { element }
    </div>
}

function AnswerBlock() {
    let element: any = <div></div>

    const videoRef = useRef<HTMLVideoElement>(null);
    if(state.answer.video != null) {
        element = <video src={`/file/${state.answer.video}`} ref={videoRef}></video>
    } else {
        element = state.answer.text;
    }

    useEffect(() => {
        if(state.answer.video != null) {
            videoRef.current.volume = 0.4;
            videoRef.current.oncanplaythrough = () => {
                setTimeout(() => videoRef.current.play(), 1000);
            }

            videoRef.current.onended = () => {
                setTimeout(() => {
                    state.answer = null;
                    render();
                }, 2000);
            }
        } else {
            setTimeout(() => {
                state.answer = null;
                render();
            }, 2000);
        }
    }, []);

    return <div className="answerBlock" style={{width: questionsTableWidth + "vw", height: (questionsTableWidth * 0.65) + "vw"}}>
        { element }
    </div>
}

function CatTrapBlock() {
    let handleAnimationEnd = (e: SyntheticEvent) => {
        state.fullscreenAnimation = false;
        render();
    }

    return <div className="fullscreen">
        <img src={preloadedMeatHookImg.src} className="meathookAnimation" onAnimationEnd={handleAnimationEnd}></img>
    </div>
}

function AdminAnswerConfirmation() {
    if(state.permissions != "ADMIN") {
        return null;
    }
    if(state.gamePhase != "QUESTION") {
        return null;
    }
    if(state.players.filter((p) => p.answers).length == 0) {
        return null;
    }

    let handleRightAnswer = (e: SyntheticEvent) => {
        sendToServer({
            type: "playerDoAnswer",
            right: true
        });
    }

    let handleWrongAnswer = (e: SyntheticEvent) => {
        sendToServer({
            type: "playerDoAnswer",
            right: false
        });
    }
    
    return <div className="adminAnswerConfirmation">
        <div>Ответ верный?</div>
        <button onClick={handleRightAnswer}>Да</button>
        <button onClick={handleWrongAnswer}>Нет</button>
    </div>
}

function Player(prop: {player: IPlayer}) {
    let avatarBase64 = state.playerAvatars.get(prop.player.name) ?? "";
    let avatarStyle = {
        backgroundColor: avatarBase64 == "" ? 'white' : 'transparent'
    }

    let handleSelectPlayer = (e: SyntheticEvent) => {
        sendToServer({
            type: "selectPlayer",
            player: prop.player.name
        });
    }

    return <div className={`player ${prop.player.answers ? 'answers' : ''} ${prop.player.selectsNextQuestion && state.gamePhase == "QUESTIONSTABLE" ? 'selectsNextQuestion' : ''} ${playerIsLocal(prop.player) ? 'local' : ''}`}>
        <div className="icons">
            {prop.player.readyToAnswer ? <img src={CheckMarkSvg}></img> : null}
        </div>
        <img src={avatarBase64} className="avatar" style={avatarStyle} onClick={() => {if(playerIsLocal(prop.player)) uploadAvatar(); }}></img>
        <div className="points">{prop.player.points}</div>
        <div className="name">{prop.player.name}</div>
        { prop.player.shouldSelectedByAdmin && state.permissions == "ADMIN" ? <button onClick={handleSelectPlayer}>Выбрать</button> : '' }
    </div>
}

class Timer extends React.Component {
    componentDidMount() {
        setInterval(() => {
            this.forceUpdate();
        }, 10);
    }

    render() {
        let k = 0;
        if(state.timerEnd > state.timerStart) {
            k = (state.timerEnd - performance.now()) / (state.timerEnd - state.timerStart);
        }
        if(k > 1.0) {
            k = 1.0;
        }
        if(k < 0.0) {
            k = 0.0;
        }
        return <div className="timer">
            <div style={{"width": `${k * 100}%`}}></div>
        </div>
    }
}

export let render = () => {
    console.log("render");
    ReactDOM.render(<App />, document.querySelector('#root'))
};