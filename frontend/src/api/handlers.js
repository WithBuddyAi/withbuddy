let logoutHandler = null;
let modalHandler = null;
let toastHandler = null;

export const setLogoutHandler = (handler) => {
  logoutHandler = handler;
};
export const setModalHandler = (handler) => {
  modalHandler = handler;
};
export const setToastHandler = (handler) => {
  toastHandler = handler;
};

export const getLogoutHandler = () => logoutHandler;
export const getModalHandler = () => modalHandler;
export const getToastHandler = () => toastHandler;
